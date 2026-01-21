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
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.CartReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.service.CartReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.CartReceiptCosmosServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

import static it.gov.pagopa.receipt.pdf.datastore.utils.HelpdeskUtils.buildErrorResponse;

/**
 * Azure Functions with Azure Http trigger.
 */
public class CartReceiptToReviewed {
    private final Logger logger = LoggerFactory.getLogger(CartReceiptToReviewed.class);

    private final CartReceiptCosmosService cartReceiptCosmosService;

    public CartReceiptToReviewed() {
        this.cartReceiptCosmosService = new CartReceiptCosmosServiceImpl();
    }

    CartReceiptToReviewed(
            CartReceiptCosmosService cartReceiptCosmosService
    ) {
        this.cartReceiptCosmosService = cartReceiptCosmosService;
    }

    /**
     * This function will be invoked when an Http Trigger occurs
     *
     * @return response with HttpStatus.OK
     */
    @FunctionName("CartReceiptToReviewed")
    public HttpResponseMessage run(
            @HttpTrigger(name = "CartReceiptToReviewedFunction",
                    methods = {HttpMethod.POST},
                    route = "cart-receipts-error/{cart-id}/reviewed",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @BindingName("cart-id") String cartId,
            @CosmosDBOutput(
                    name = "CartReceiptErrorDatastore",
                    databaseName = "db",
                    containerName = "cart-receipts-message-errors",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<CartReceiptError> documentdb,
            final ExecutionContext context
    ) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        String responseMsg;
        CartReceiptError receiptError;

        try {
            receiptError = cartReceiptCosmosService.getCartReceiptError(cartId);
        } catch (NoSuchElementException | CartNotFoundException e) {
            responseMsg = String.format("No cartReceiptError has been found with cartId %s", cartId);
            logger.error("[{}] {}", context.getFunctionName(), responseMsg, e);
            return buildErrorResponse(request, HttpStatus.NOT_FOUND, responseMsg);
        }

        if (!ReceiptErrorStatusType.TO_REVIEW.equals(receiptError.getStatus())) {
            responseMsg = String.format("Found cartReceiptError with invalid status %s for cartId %s", receiptError.getStatus(), cartId);
            return buildErrorResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, responseMsg);
        }

        receiptError.setStatus(ReceiptErrorStatusType.REVIEWED);
        documentdb.setValue(receiptError);

        responseMsg = String.format("CartReceiptError with cartId %s updated to status %s with success", receiptError.getId(), ReceiptErrorStatusType.REVIEWED);
        return request.createResponseBuilder(HttpStatus.OK).body(responseMsg).build();
    }

}
