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
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventBadRequestException;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventUnprocessableEntityException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.datastore.service.HelpdeskService;
import it.gov.pagopa.receipt.pdf.datastore.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.HelpdeskServiceImpl;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.ReceiptCosmosServiceImpl;
import it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils.isReceiptStatusValid;
import static it.gov.pagopa.receipt.pdf.datastore.utils.HelpdeskUtils.buildErrorResponse;

/**
 * Azure Functions with Azure Http trigger.
 */
public class RecoverFailedReceipt {

    private final Logger logger = LoggerFactory.getLogger(RecoverFailedReceipt.class);

    private final ReceiptCosmosService receiptCosmosService;
    private final HelpdeskService helpdeskService;

    public RecoverFailedReceipt() {
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
        this.helpdeskService = new HelpdeskServiceImpl();
    }

    RecoverFailedReceipt(ReceiptCosmosService receiptCosmosService, HelpdeskService helpdeskService) {
        this.receiptCosmosService = receiptCosmosService;
        this.helpdeskService = helpdeskService;
    }

    /**
     * This function will be invoked when a Http Trigger occurs.
     * <p>
     * The function is responsible for retrieving receipts that are in a {@link ReceiptStatusType#INSERTED},
     * {@link ReceiptStatusType#FAILED} and {@link ReceiptStatusType#NOT_QUEUE_SENT} state.
     * For a single receipt, the function should:
     * <ul>
     *      <li>try to retrieve the biz event -> if it doesn't find it, error</li>
     *      <li>check that it's a valid biz: {@link BizEventToReceiptUtils#isBizEventInvalid(BizEvent)} -> if invalid, error</li>
     *      <li>check that it's not a cart biz: {@link BizEventToReceiptUtils#getTotalNotice(BizEvent, Logger)} == 1 -> if cart, error</li>
     *      <li>check that the receipt is in one of the 3 manageable states -> if not, error</li>
     *      <li>recreate the receipt from the biz: {@link BizEventToReceiptUtils#createReceipt(BizEvent, BizEventToReceiptService, Logger)} </li>
     *      <li>if everything is OK, it updates the receipt on the cosmos. {@link BizEventToReceiptService#handleSaveReceipt(Receipt)}</li>
     *      <li>if everything is OK, send it to the queue {@link BizEventToReceiptService#handleSendMessageToQueue(List, Receipt)}</li>
     * </ul>
     * <p>
     * It recovers the receipt with the specified biz event id that has the following status:
     * <ul>
     *     <li>{@link ReceiptStatusType#INSERTED}</li>
     *     <li>{@link ReceiptStatusType#FAILED}</li>
     *     <li>{@link ReceiptStatusType#NOT_QUEUE_SENT}</li>
     * </ul>
     * <p>
     * It creates the receipts if not exist and send on queue the event in order to proceed with the receipt generation.
     *
     * @return response with {@link HttpStatus#OK} if the operation succeeded
     */
    @FunctionName("RecoverFailedReceipt")
    public HttpResponseMessage run(
            @HttpTrigger(name = "RecoverFailedReceiptTrigger",
                    methods = {HttpMethod.POST},
                    route = "receipts/{event-id}/recover-failed",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("event-id") String eventId,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    containerName = "receipts",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<Receipt> documentdb,
            final ExecutionContext context
    ) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        Receipt existingReceipt;
        try {
            existingReceipt = this.receiptCosmosService.getReceipt(eventId);
        } catch (ReceiptNotFoundException e) {
            String errMsg = "Receipt not found with the provided event id";
            logger.error(errMsg, e);
            return buildErrorResponse(request, HttpStatus.NOT_FOUND, errMsg);
        }

        if (existingReceipt.getStatus() == null || existingReceipt.getStatus().isNotAFailedDatastoreStatus()) {
            String errMsg = String.format(
                    "The provided receipt is in status %s, which is not among the processable " +
                            "statuses (INSERTED, NOT_QUEUE_SENT, FAILED).",
                    existingReceipt.getStatus()
            );
            logger.error(errMsg);
            return buildErrorResponse(request, HttpStatus.UNPROCESSABLE_ENTITY, errMsg);
        }

        Receipt receipt;
        try {
            receipt = this.helpdeskService.recoverFailedReceipt(existingReceipt);
        } catch (BizEventUnprocessableEntityException e) {
            logger.error(e.getMessage(), e);
            return buildErrorResponse(request, HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } catch (BizEventBadRequestException | BizEventNotFoundException e) {
            logger.error(e.getMessage(), e);
            return buildErrorResponse(request, HttpStatus.BAD_REQUEST, e.getMessage());
        }

        if (!isReceiptStatusValid(receipt)) {
            String errMsg = String.format("Recover failed for receipt with id %s. Reason: %s",
                    eventId, receipt.getReasonErr() != null ? receipt.getReasonErr().getMessage() : "Detail unavailable");
            logger.error(errMsg);
            documentdb.setValue(receipt);
            return buildErrorResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, errMsg);
        }

        String responseMsg = String.format("Receipt with eventId %s recovered", eventId);
        return request.createResponseBuilder(HttpStatus.OK)
                .body(responseMsg)
                .build();
    }
}