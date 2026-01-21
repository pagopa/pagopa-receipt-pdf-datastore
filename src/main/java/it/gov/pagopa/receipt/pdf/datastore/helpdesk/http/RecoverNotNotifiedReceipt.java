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
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.service.HelpdeskService;
import it.gov.pagopa.receipt.pdf.datastore.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.HelpdeskServiceImpl;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.ReceiptCosmosServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Optional;

import static it.gov.pagopa.receipt.pdf.datastore.utils.HelpdeskUtils.buildErrorResponse;

/**
 * Azure Functions with HTTP Trigger.
 */
public class RecoverNotNotifiedReceipt {

    private final Logger logger = LoggerFactory.getLogger(RecoverNotNotifiedReceipt.class);

    private final ReceiptCosmosService receiptCosmosService;
    private final HelpdeskService helpdeskService;

    public RecoverNotNotifiedReceipt() {
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
        this.helpdeskService = new HelpdeskServiceImpl();
    }

    RecoverNotNotifiedReceipt(ReceiptCosmosService receiptCosmosService, HelpdeskService helpdeskService) {
        this.receiptCosmosService = receiptCosmosService;
        this.helpdeskService = helpdeskService;
    }

    /**
     * This function will be invoked when an Http Trigger occurs.
     * <p>
     * It recovers the receipt with the specified biz event id
     * <p>
     * It recovers the receipt with failed notification ({@link ReceiptStatusType#IO_ERROR_TO_NOTIFY}) or notification
     * not triggered ({@link ReceiptStatusType#GENERATED} by clearing the errors and update the status to the
     * previous step ({@link ReceiptStatusType#GENERATED}).
     *
     * @return response with {@link HttpStatus#OK} if the operation succeeded
     */
    @FunctionName("RecoverNotNotifiedReceipt")
    public HttpResponseMessage run(
            @HttpTrigger(name = "RecoverNotNotifiedTrigger",
                    methods = {HttpMethod.POST},
                    route = "receipts/{event-id}/recover-not-notified",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("event-id") String eventId,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    containerName = "receipts",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<Receipt> documentReceipts,
            final ExecutionContext context
    ) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        Receipt receipt;
        try {
            receipt = this.receiptCosmosService.getReceipt(eventId);
        } catch (ReceiptNotFoundException e) {
            String errMsg = String.format("Unable to retrieve the receipt with eventId %s", eventId);
            logger.error("[{}] {}", context.getFunctionName(), errMsg, e);
            return buildErrorResponse(request, HttpStatus.NOT_FOUND, errMsg);
        }

        if (receipt.getStatus() == null || !receipt.getStatus().isANotificationFailedStatus()) {
            String errMsg = String.format("The requested receipt with eventId %s is not in the expected status",
                    receipt.getEventId());
            logger.error(errMsg);
            return buildErrorResponse(request, HttpStatus.UNPROCESSABLE_ENTITY, errMsg);
        }

        Receipt restoredReceipt = this.helpdeskService.recoverNoNotifiedReceipt(receipt);

        documentReceipts.setValue(restoredReceipt);
        String responseMsg = String.format("Receipt with id %s and eventId %s restored in status %s with success",
                receipt.getId(), receipt.getEventId(), ReceiptStatusType.GENERATED);
        return request.createResponseBuilder(HttpStatus.OK).body(responseMsg).build();
    }
}
