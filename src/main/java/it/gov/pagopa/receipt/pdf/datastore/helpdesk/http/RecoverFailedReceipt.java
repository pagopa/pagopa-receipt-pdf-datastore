package it.gov.pagopa.receipt.pdf.datastore.helpdesk.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.receipt.pdf.datastore.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventBadRequestException;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.datastore.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.datastore.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.BizEventToReceiptServiceImpl;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.ReceiptCosmosServiceImpl;
import it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Azure Functions with Azure Http trigger.
 */
public class RecoverFailedReceipt {

    private final Logger logger = LoggerFactory.getLogger(RecoverFailedReceipt.class);

    private final BizEventToReceiptService bizEventToReceiptService;
    private final BizEventCosmosClient bizEventCosmosClient;
    private final ReceiptCosmosService receiptCosmosService;

    public RecoverFailedReceipt(){
        this.bizEventToReceiptService = new BizEventToReceiptServiceImpl();
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
        this.bizEventCosmosClient = BizEventCosmosClientImpl.getInstance();
    }

    RecoverFailedReceipt(BizEventToReceiptService bizEventToReceiptService,
                         BizEventCosmosClient bizEventCosmosClient,
                         ReceiptCosmosService receiptCosmosService){
        this.bizEventToReceiptService = bizEventToReceiptService;
        this.bizEventCosmosClient = bizEventCosmosClient;
        this.receiptCosmosService = receiptCosmosService;
    }

    /**
     * This function will be invoked when an Http Trigger occurs.
     * The function is responsible for retrieving receipts that are in a FAILED, INSERTED and NOT_QUEUE_SENT state.
     * For a single receipt, the function should:
     *  - try to retrieve the biz event -> if it doesn't find it, error
     *  - check that it's a valid biz: BizEventToReceiptUtils.isBizEventInvalid() -> if invalid, error
     *  - check that it's not a cart biz: BizEventToReceiptUtils.getTotalNotice() == 1 -> if cart, error
     *  - check that the receipt is in one of the 3 manageable states: FAILED, INSERTED, and NOT_QUEUE_SENT -> if not, error
     *  - recreate the receipt from the biz: BizEventToReceiptUtils.createReceipt()
     *  - if everything is OK, it updates the receipt on the cosmos. BizEventToReceiptService.handleSaveReceipt()
     *  - if everything is OK, send it to the queue BizEventToReceiptService.handleSendMessageToQueue()
     * <p>
     * It recovers the receipt with the specified biz event id that has the following status:
     * - ({@link ReceiptStatusType#INSERTED})
     * - ({@link ReceiptStatusType#FAILED})
     * - ({@link ReceiptStatusType#NOT_QUEUE_SENT})
     * <p>
     * It creates the receipts if not exist and send on queue the event in order to proceed with the receipt generation.
     *
     * @return response with {@link HttpStatus#OK} if the operation succeeded
     */
    @FunctionName("RecoverFailedReceipt")
    public HttpResponseMessage run (
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
            final ExecutionContext context) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        try {
            Receipt receipt = BizEventToReceiptUtils.getEvent(eventId, context, this.bizEventToReceiptService,
                    this.bizEventCosmosClient, this.receiptCosmosService, null, logger);

            documentdb.setValue(receipt);
            if (BizEventToReceiptUtils.isReceiptStatusValid(receipt)) {
                String responseMsg = String.format("Receipt with eventId %s recovered", eventId);
                return request.createResponseBuilder(HttpStatus.OK)
                        .body(responseMsg)
                        .build();
            } else {
                return request
                        .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ProblemJson.builder()
                                .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                                .detail(receipt.getReasonErr().getMessage())
                                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                                .build())
                        .build();
            }
        } catch (BizEventNotFoundException | BizEventBadRequestException exception) {
            String msg = String.format("Unable to retrieve the biz-event with id %s", eventId);
            logger.error(msg, exception);
            return request
                    .createResponseBuilder(exception.getHttpStatus())
                    .body(ProblemJson.builder()
                            .title(exception.getHttpStatus().name())
                            .detail(msg)
                            .status(exception.getHttpStatus().value())
                            .build())
                    .build();
        } catch (PDVTokenizerException | JsonProcessingException e) {
            logger.error(e.getMessage(), e);
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
                            .detail(e.getMessage())
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .build())
                    .build();
        } catch (ReceiptNotFoundException e) {
            logger.error(e.getMessage(), e);
            return request
                    .createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.BAD_REQUEST.name())
                            .detail(e.getMessage())
                            .status(HttpStatus.BAD_REQUEST.value())
                            .build())
                    .build();
        }
    }
}