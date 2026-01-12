package it.gov.pagopa.receipt.pdf.datastore.helpdesk.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.receipt.pdf.datastore.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventBadRequestException;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.datastore.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.datastore.service.CartReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.BizEventToReceiptServiceImpl;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.CartReceiptCosmosServiceImpl;
import it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils;
import it.gov.pagopa.receipt.pdf.datastore.utils.TransactionEventToCartReceiptUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Azure Functions with Azure Http trigger.
 */
public class CartRecoverFailedReceipt {

    private final Logger logger = LoggerFactory.getLogger(CartRecoverFailedReceipt.class);

    private final BizEventToReceiptService bizEventToReceiptService;
    private final CartReceiptCosmosService cartReceiptCosmosService;

    public CartRecoverFailedReceipt(){
        this.cartReceiptCosmosService = new CartReceiptCosmosServiceImpl();
        this.bizEventToReceiptService = new BizEventToReceiptServiceImpl();
    }

    CartRecoverFailedReceipt(BizEventToReceiptService bizEventToReceiptService,
                             CartReceiptCosmosService cartReceiptCosmosService){
        this.bizEventToReceiptService = bizEventToReceiptService;
        this.cartReceiptCosmosService = cartReceiptCosmosService;
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
    public HttpResponseMessage run (
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
            final ExecutionContext context) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        // TODO
        // a partire dal cartId recuperare i biz-event
        //  validazione dei biz-event
        // controllare che il numero di biz-event con il total notice presente nei biz
        // se i biz sono validi procedere con la ricreazione della cart e l'invio in coda
        // vedere regenerate sul recupero dell'id e sovrascrittura

        try {
            // retrieve biz-event with the specified cartId
            List<BizEvent> bizEvents = bizEventToReceiptService.getCartBizEventsById(cartId);
            for(BizEvent bizEvent : bizEvents) {
                // biz-event validation
                if (BizEventToReceiptUtils.isBizEventInvalid(bizEvent, context, logger)) {
                    String errMsg = String.format("Biz-event with id %s is invalid", bizEvent.getId());
                    throw new BizEventBadRequestException(errMsg);
                }

                // total notice check
                Integer totalNotice = BizEventToReceiptUtils.getTotalNotice(bizEvent, context, logger);
                if (totalNotice != bizEvents.size()) {
                    String errMsg = String.format("Failed to regenerate cart, the expected total notice %s does not match the number of biz events %s",
                            totalNotice, bizEvents.size());
                    logger.error(errMsg);
                    return request
                            .createResponseBuilder(HttpStatus.UNPROCESSABLE_ENTITY)
                            .body(ProblemJson.builder()
                                    .title(HttpStatus.UNPROCESSABLE_ENTITY.name())
                                    .detail(errMsg)
                                    .status(HttpStatus.UNPROCESSABLE_ENTITY.value())
                                    .build())
                            .build();
                }
            }

            // all ok, proceed with the cart recovery
            return null;

//            CartForReceipt receipt = TransactionEventToCartReceiptUtils.retrieveCartAndSendReceipt(
//                    cartId, context, this.cartReceiptCosmosService,
//                    this.bizEventCosmosClient,  null, logger);
//
//            documentdb.setValue(receipt);
//            if (BizEventToReceiptUtils.isReceiptStatusValid(receipt)) {
//                String responseMsg = String.format("Receipt with cartId %s recovered", cartId);
//                return request.createResponseBuilder(HttpStatus.OK)
//                        .body(responseMsg)
//                        .build();
//            } else {
//                return request
//                        .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
//                        .body(ProblemJson.builder()
//                                .title(HttpStatus.INTERNAL_SERVER_ERROR.name())
//                                .detail(receipt.getReasonErr().getMessage())
//                                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
//                                .build())
//                        .build();
//            }
        } catch (BizEventBadRequestException ex) {
            String msg = String.format("Problem with the biz-events related to cartId %s", cartId);
            logger.error(msg, ex);
            return request
                    .createResponseBuilder(ex.getHttpStatus())
                    .body(ProblemJson.builder()
                            .title(ex.getHttpStatus().name())
                            .detail(msg)
                            .status(ex.getHttpStatus().value())
                            .build())
                    .build();

        } catch (BizEventNotFoundException | BizEventBadRequestException exception) {
            String msg = String.format("Unable to retrieve the biz-event with id %s", cartId);
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