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
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.service.CartReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.datastore.service.HelpdeskService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.CartReceiptCosmosServiceImpl;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.HelpdeskServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Optional;

import static it.gov.pagopa.receipt.pdf.datastore.utils.HelpdeskUtils.buildErrorResponse;

/**
 * Azure Functions with HTTP Trigger.
 */
public class RecoverNotNotifiedCartReceipt {

    private final Logger logger = LoggerFactory.getLogger(RecoverNotNotifiedCartReceipt.class);

    private final CartReceiptCosmosService cartReceiptCosmosService;
    private final HelpdeskService helpdeskService;

    public RecoverNotNotifiedCartReceipt() {
        this.cartReceiptCosmosService = new CartReceiptCosmosServiceImpl();
        this.helpdeskService = new HelpdeskServiceImpl();
    }

    RecoverNotNotifiedCartReceipt(CartReceiptCosmosService cartReceiptCosmosService, HelpdeskService helpdeskService) {
        this.cartReceiptCosmosService = cartReceiptCosmosService;
        this.helpdeskService = helpdeskService;
    }

    /**
     * This function will be invoked when a Http Trigger occurs.
     * <p>
     * It recovers the receipt with the specified biz event id
     * <p>
     * It recovers the receipt with failed notification ({@link CartStatusType#IO_ERROR_TO_NOTIFY}) or notification
     * not triggered ({@link CartStatusType#GENERATED} by clearing the errors and update the status to the
     * previous step ({@link CartStatusType#GENERATED}).
     *
     * @return response with {@link HttpStatus#OK} if the operation succeeded
     */
    @FunctionName("RecoverNotNotifiedCartReceipt")
    public HttpResponseMessage run(
            @HttpTrigger(name = "RecoverNotNotifiedCartTrigger",
                    methods = {HttpMethod.POST},
                    route = "cart-receipts/{cart-id}/recover-not-notified",
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

        CartForReceipt cart;
        try {
            cart = this.cartReceiptCosmosService.getCart(cartId);
        } catch (CartNotFoundException e) {
            String errMsg = String.format("Unable to retrieve the cart receipt with id %s", cartId);
            logger.error("[{}] {}", context.getFunctionName(), errMsg, e);
            return buildErrorResponse(request, HttpStatus.NOT_FOUND, errMsg);
        }

        if (cart.getStatus() == null || !cart.getStatus().isANotificationFailedStatus()) {
            String errMsg = String.format("The requested cart receipt with id %s is not in the expected status",
                    cart.getEventId());
            logger.error(errMsg);
            return buildErrorResponse(request, HttpStatus.UNPROCESSABLE_ENTITY, errMsg);
        }

        CartForReceipt restoredCart = this.helpdeskService.recoverNoNotifiedCart(cart);

        documentdb.setValue(restoredCart);
        String responseMsg = String.format("Cart receipt with id %s restored in status %s with success",
                cart.getEventId(), ReceiptStatusType.GENERATED);
        return request.createResponseBuilder(HttpStatus.OK).body(responseMsg).build();
    }
}
