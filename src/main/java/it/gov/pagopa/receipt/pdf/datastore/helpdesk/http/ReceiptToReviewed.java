package it.gov.pagopa.receipt.pdf.datastore.helpdesk.http;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.ReceiptCosmosServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

import static it.gov.pagopa.receipt.pdf.datastore.utils.HelpdeskUtils.buildErrorResponse;

/**
 * Azure Functions with Azure Http trigger.
 */
public class ReceiptToReviewed {
    private final Logger logger = LoggerFactory.getLogger(ReceiptToReviewed.class);

    private final ReceiptCosmosService receiptCosmosService;

    public ReceiptToReviewed() {
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
    }

    ReceiptToReviewed(
            ReceiptCosmosService receiptCosmosService
    ) {
        this.receiptCosmosService = receiptCosmosService;
    }

    /**
     * This function will be invoked when a Http Trigger occurs
     * <p>
     * The function is responsible for retrieving a receipt-error with the provided event-id.
     * If the retrieved document is in the expected status {@link ReceiptErrorStatusType#TO_REVIEW} updates the status
     * to {@link ReceiptErrorStatusType#REVIEWED} and saves it.
     *
     * @return response with {@link HttpStatus#OK} if the operation succeeded
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
            final ExecutionContext context
    ) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        String responseMsg;
        ReceiptError receiptError;

        try {
            receiptError = receiptCosmosService.getReceiptError(eventId);
        } catch (NoSuchElementException | ReceiptNotFoundException e) {
            responseMsg = String.format("No receiptError has been found with bizEventId %s", eventId);
            logger.error("[{}] {}", context.getFunctionName(), responseMsg, e);
            return buildErrorResponse(request, HttpStatus.NOT_FOUND, responseMsg);
        }

        if (!ReceiptErrorStatusType.TO_REVIEW.equals(receiptError.getStatus())) {
            responseMsg = String.format("Found receiptError with invalid status %s for bizEventId %s", receiptError.getStatus(), eventId);
            return buildErrorResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, responseMsg);
        }

        receiptError.setStatus(ReceiptErrorStatusType.REVIEWED);
        documentdb.setValue(receiptError);

        responseMsg = String.format("ReceiptError with id %s and bizEventId %s updated to status %s with success", receiptError.getId(), receiptError.getBizEventId(), ReceiptErrorStatusType.REVIEWED);
        return request.createResponseBuilder(HttpStatus.OK).body(responseMsg).build();
    }

}
