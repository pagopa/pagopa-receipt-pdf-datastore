package it.gov.pagopa.receipt.pdf.datastore.helpdesk;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.model.ProblemJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Azure Functions with Azure Http trigger.
 */
public class ReceiptToReviewed {
    private final Logger logger = LoggerFactory.getLogger(ReceiptToReviewed.class);
    private final ReceiptCosmosClient receiptCosmosClient;

    public ReceiptToReviewed() {
        this.receiptCosmosClient = ReceiptCosmosClientImpl.getInstance();
    }

    ReceiptToReviewed(
            ReceiptCosmosClient receiptCosmosClient) {
        this.receiptCosmosClient = receiptCosmosClient;
    }

    /**
     * This function will be invoked when an Http Trigger occurs
     *
     * @return response with HttpStatus.OK
     */
    @FunctionName("ReceiptToReviewed")
    public HttpResponseMessage run(
            @HttpTrigger(name = "ReceiptToReviewedFunction",
                    methods = {HttpMethod.POST},
                    route = "receipts-error/{event-id}/reviewed",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("event-id") String eventId,
            @CosmosDBOutput(
                    name = "ReceiptErrorDatastore",
                    databaseName = "db",
                    containerName = "receipts-message-errors",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<ReceiptError> documentdb,
            final ExecutionContext context) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        if (eventId == null || eventId.isBlank()) {
            return request
                    .createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.BAD_REQUEST.name())
                            .detail("Please pass a valid biz-event id")
                            .status(HttpStatus.BAD_REQUEST.value())
                            .build())
                    .build();
        }

        String responseMsg;
        ReceiptError receiptError;

        try {
            receiptError = receiptCosmosClient.getReceiptError(eventId);
        } catch (NoSuchElementException | ReceiptNotFoundException e) {
            responseMsg = String.format("No receiptError has been found with bizEventId %s", eventId);
            logger.error("[{}] {}", context.getFunctionName(), responseMsg, e);
            return request
                    .createResponseBuilder(HttpStatus.NOT_FOUND)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.NOT_FOUND.name())
                            .detail(responseMsg)
                            .status(HttpStatus.NOT_FOUND.value())
                            .build())
                    .build();
        }

        if (!receiptError.getStatus().equals(ReceiptErrorStatusType.TO_REVIEW)) {
            responseMsg = String.format("Found receiptError with invalid status %s for bizEventId %s", receiptError.getStatus(), eventId);
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                            .detail(responseMsg)
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .build())
                    .build();
        }
        receiptError.setStatus(ReceiptErrorStatusType.REVIEWED);

        documentdb.setValue(receiptError);

        responseMsg = String.format("ReceiptError with id %s and bizEventId %s updated to status %s with success", receiptError.getId(), receiptError.getBizEventId(), ReceiptErrorStatusType.REVIEWED);
        return request.createResponseBuilder(HttpStatus.OK).body(responseMsg).build();
    }

}
