package it.gov.pagopa.receipt.pdf.datastore.helpdesk.http;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.CartReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.datastore.service.CartReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.CartReceiptCosmosServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

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
     * TODO
     * collection cart-receipt-message-errors
     * per il carrello arriva l'id del carrello (id collection)
     * logica analoga a quanto presente in ReceiptToReviewed
     *
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
            final ExecutionContext context) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        if (cartId == null || cartId.isBlank()) {
            return request
                    .createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.BAD_REQUEST.name())
                            .detail("Please pass a valid cart id")
                            .status(HttpStatus.BAD_REQUEST.value())
                            .build())
                    .build();
        }

        String responseMsg;
        CartReceiptError receiptError;

        try {
            receiptError = cartReceiptCosmosService.getCartReceiptError(cartId);
        } catch (NoSuchElementException | CartNotFoundException e) {
            responseMsg = String.format("No cartReceiptError has been found with cartId %s", cartId);
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
            responseMsg = String.format("Found cartReceiptError with invalid status %s for cartId %s", receiptError.getStatus(), cartId);
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

        responseMsg = String.format("CartReceiptError with cartId %s updated to status %s with success", receiptError.getId(), ReceiptErrorStatusType.REVIEWED);
        return request.createResponseBuilder(HttpStatus.OK).body(responseMsg).build();
    }

}
