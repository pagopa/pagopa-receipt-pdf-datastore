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
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventBadRequestException;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventUnprocessableEntityException;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.service.HelpdeskService;
import it.gov.pagopa.receipt.pdf.datastore.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.HelpdeskServiceImpl;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.ReceiptCosmosServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Optional;

import static it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils.buildErrorResponse;
import static it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils.isCartStatusValid;

/**
 * Azure Functions with Azure Http trigger.
 */
public class RecoverFailedCartReceipt {

    private final Logger logger = LoggerFactory.getLogger(RecoverFailedCartReceipt.class);

    private final ReceiptCosmosService receiptCosmosService;
    private final HelpdeskService helpdeskService;

    public RecoverFailedCartReceipt() {
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
        this.helpdeskService = new HelpdeskServiceImpl();
    }

    RecoverFailedCartReceipt(
            ReceiptCosmosService receiptCosmosService,
            HelpdeskService helpdeskService
    ) {
        this.receiptCosmosService = receiptCosmosService;
        this.helpdeskService = helpdeskService;
    }

    /**
     * This function will be invoked when an Http Trigger occurs.
     * The function is responsible for retrieving cart receipts that are in a FAILED, INSERTED and NOT_QUEUE_SENT state.
     * For a cart receipt, the function should:
     * <ul>
     *     <li> try to retrieve cart receipt -> if it doesn't find it, error</li>
     *     <li> check cart receipt {@link CartStatusType}-> if it is no among the processable ones, error</li>
     *     <li> try to retrieve the list of biz events -> if it doesn't find it, error</li>
     *     <li> check that the biz events are valid: BizEventToReceiptUtils.isBizEventInvalid() -> if invalid, error</li>
     *     <li> check that the biz events are valid: BizEventToReceiptUtils.getTotalNotice() == biz event list size -> if not, error</li>
     *     <li> recreate the receipt from the biz</li>
     *     <li> if everything is OK, it updates the receipt on the cosmos</li>
     *     <li> if everything is OK, send it to the queue</li>
     * </ul>
     * It recovers the receipt with the specified biz event id that has the following status:
     * - ({@link CartStatusType#INSERTED})
     * - ({@link CartStatusType#FAILED})
     * - ({@link CartStatusType#NOT_QUEUE_SENT})
     * <p>
     * It creates the receipts if not exist and send on queue the event in order to proceed with the receipt generation.
     *
     * @return response with {@link HttpStatus#OK} if the operation succeeded
     */
    @FunctionName("RecoverFailedCartReceipt")
    public HttpResponseMessage run(
            @HttpTrigger(name = "RecoverFailedCartReceiptTrigger",
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

        CartForReceipt existingCart;
        try {
            existingCart = this.receiptCosmosService.getCart(cartId);
        } catch (CartNotFoundException e) {
            String errMsg = "Cart receipt not found with the provided cart id";
            logger.error(errMsg, e);
            return buildErrorResponse(request, HttpStatus.NOT_FOUND, errMsg);
        }

        if (isCartStatusNotProcessable(existingCart.getStatus())) {
            String errMsg = String.format(
                    "The provided cart is in status %s, which is not among the processable " +
                            "statuses (INSERTED, NOT_QUEUE_SENT, FAILED).",
                    existingCart.getStatus()
            );
            logger.error(errMsg);
            return buildErrorResponse(request, HttpStatus.UNPROCESSABLE_ENTITY, errMsg);
        }

        CartForReceipt cartForReceipt;
        try {
            cartForReceipt = this.helpdeskService.recoverFailedCart(existingCart);
        } catch (BizEventUnprocessableEntityException e) {
            logger.error(e.getMessage(), e);
            return buildErrorResponse(request, HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        } catch (BizEventBadRequestException e) {
            logger.error(e.getMessage(), e);
            return buildErrorResponse(request, HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (PDVTokenizerException | JsonProcessingException e) {
            logger.error(e.getMessage(), e);
            return buildErrorResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }

        if (!isCartStatusValid(cartForReceipt)) {
            String errMsg = String.format("Recover failed for cart with id %s. Reason: %s",
                    cartId, cartForReceipt.getReasonErr().getMessage());
            logger.error(errMsg);
            documentdb.setValue(cartForReceipt);
            return buildErrorResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, errMsg);
        }

        String responseMsg = String.format("Cart receipt with id %s recovered", cartId);
        return request.createResponseBuilder(HttpStatus.OK)
                .body(responseMsg)
                .build();
    }

    private boolean isCartStatusNotProcessable(CartStatusType status) {
        return !CartStatusType.INSERTED.equals(status)
                && !CartStatusType.NOT_QUEUE_SENT.equals(status)
                && !CartStatusType.FAILED.equals(status);
    }
}