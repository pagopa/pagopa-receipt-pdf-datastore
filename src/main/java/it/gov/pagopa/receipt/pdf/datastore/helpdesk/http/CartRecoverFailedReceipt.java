package it.gov.pagopa.receipt.pdf.datastore.helpdesk.http;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import it.gov.pagopa.receipt.pdf.datastore.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventBadRequestException;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventUnprocessableEntityException;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.datastore.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.datastore.service.HelpdeskService;
import it.gov.pagopa.receipt.pdf.datastore.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.BizEventToReceiptServiceImpl;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.HelpdeskServiceImpl;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.ReceiptCosmosServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils.hasCartInvalidStatus;
import static it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils.isCartStatusValid;

/**
 * Azure Functions with Azure Http trigger.
 */
public class CartRecoverFailedReceipt {

    private final Logger logger = LoggerFactory.getLogger(CartRecoverFailedReceipt.class);

    private final BizEventToReceiptService bizEventToReceiptService;
    private final ReceiptCosmosService receiptCosmosService;
    private final BizEventCosmosClient bizEventCosmosClient;
    private final HelpdeskService helpdeskService;

    public CartRecoverFailedReceipt() {
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
        this.bizEventToReceiptService = new BizEventToReceiptServiceImpl();
        this.bizEventCosmosClient = BizEventCosmosClientImpl.getInstance();
        this.helpdeskService = new HelpdeskServiceImpl();
    }

    CartRecoverFailedReceipt(
            BizEventToReceiptService bizEventToReceiptService,
            ReceiptCosmosService receiptCosmosService,
            BizEventCosmosClient bizEventCosmosClient,
            HelpdeskService helpdeskService
    ) {
        this.bizEventToReceiptService = bizEventToReceiptService;
        this.receiptCosmosService = receiptCosmosService;
        this.bizEventCosmosClient = bizEventCosmosClient;
        this.helpdeskService = helpdeskService;
    }

    /**
     * This function will be invoked when an Http Trigger occurs.
     * The function is responsible for retrieving receipts that are in a FAILED, INSERTED and NOT_QUEUE_SENT state.
     * TODO collection cart-for-receipts
     * TODO https://github.com/pagopa/pagopa-receipt-pdf-generator/pull/170/changes#diff-5809f1fa1db9b483c00c90153ca5dc50b57437d89bb6f96b50f9447430ecd440
     * TODO vedere impl https://github.com/pagopa/pagopa-receipt-pdf-datastore/blob/b0403d8b614a97e02e2af713737b1054fa693a19/src/main/java/it/gov/pagopa/receipt/pdf/datastore/BizEventToReceipt.java#L143
     * For a cart receipt, the function should:
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
    @FunctionName("CartRecoverFailedReceipt")
    public HttpResponseMessage run(
            @HttpTrigger(name = "CartRecoverFailedReceiptTrigger",
                    methods = {HttpMethod.POST},
                    route = "cart-receipts/{cart-id}/recover-failed",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("cart-id") String cartId,
            @CosmosDBOutput(
                    name = "CartReceiptDatastore",
                    databaseName = "db",
                    containerName = "cart-for-receipts",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<CartForReceipt> documentdb,
            final ExecutionContext context
    ) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        // TODO
        // a partire dal cartId recuperare i biz-event
        //  validazione dei biz-event
        // controllare che il numero di biz-event con il total notice presente nei biz
        // se i biz sono validi procedere con la ricreazione della cart e l'invio in coda
        // vedere regenerate sul recupero dell'id e sovrascrittura

        CartForReceipt existingCart;
        try {
            existingCart = this.receiptCosmosService.getCart(cartId);
        } catch (CartNotFoundException e) {
            String errMsg = "Cart receipt not found with the provided cart id";
            logger.error(errMsg, e);
            return buildErrorResponse(request, HttpStatus.NOT_FOUND, errMsg);
        }

        if (!hasCartInvalidStatus(existingCart.getStatus())) {
            String errMsg = String.format(
                    "The provided cart is in status %s, which is not among the processable " +
                            "statuses (INSERTED, NOT_QUEUE_SENT, FAILED).",
                    existingCart.getStatus()
            );
            logger.error(errMsg);
            return buildErrorResponse(request, HttpStatus.UNPROCESSABLE_ENTITY, errMsg);
        }

        // retrieve biz-event with the specified cartId
        List<BizEvent> bizEvents = this.bizEventCosmosClient.getAllCartBizEventDocument(cartId);
        try {
            this.helpdeskService.validateCartBizEvents(bizEvents);
        } catch (BizEventBadRequestException e) {
            return buildErrorResponse(request, HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (BizEventUnprocessableEntityException e) {
            return buildErrorResponse(request, HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }

        CartForReceipt cartForReceipt;
        try {
            cartForReceipt = this.bizEventToReceiptService.buildCartFromBizEventList(bizEvents);
            cartForReceipt.set_etag(existingCart.get_etag());
        } catch (PDVTokenizerException | JsonProcessingException e) {
            logger.error(e.getMessage(), e);
            return buildErrorResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }

        if (isCartStatusValid(cartForReceipt)) {
            // saved on CosmosDB
            cartForReceipt = this.bizEventToReceiptService.saveCartForReceiptWithoutRetry(cartForReceipt);
        }

        if (isCartStatusValid(cartForReceipt)) {
            this.bizEventToReceiptService.handleSendCartMessageToQueue(bizEvents, cartForReceipt);
        }

        if (!isCartStatusValid(cartForReceipt)) {
            String errMsg = String.format("Recover failed for cart with id %s. Reason: %s",
                    cartId, cartForReceipt.getReasonErr().getMessage());
            logger.error(errMsg);
            documentdb.setValue(cartForReceipt);
            return buildErrorResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, errMsg);
        }

        String responseMsg = String.format("Receipt with eventId %s recovered", cartId);
        return request.createResponseBuilder(HttpStatus.OK)
                .body(responseMsg)
                .build();
    }

    private HttpResponseMessage buildErrorResponse(
            HttpRequestMessage<Optional<String>> request,
            HttpStatus httpStatus,
            String errMsg
    ) {
        return request
                .createResponseBuilder(httpStatus)
                .body(ProblemJson.builder()
                        .title(httpStatus.name())
                        .detail(errMsg)
                        .status(httpStatus.value())
                        .build())
                .build();
    }
}