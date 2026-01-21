package it.gov.pagopa.receipt.pdf.datastore.service.impl;

import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.datastore.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.CartReceiptsCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.CartReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.service.CartReceiptCosmosService;

public class CartReceiptCosmosServiceImpl implements CartReceiptCosmosService {

    private final CartReceiptsCosmosClient cartReceiptsCosmosClient;

    public CartReceiptCosmosServiceImpl() {
        this.cartReceiptsCosmosClient = CartReceiptsCosmosClientImpl.getInstance();
    }

    CartReceiptCosmosServiceImpl(CartReceiptsCosmosClient cartReceiptsCosmosClient) {
        this.cartReceiptsCosmosClient = cartReceiptsCosmosClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CartReceiptError getCartReceiptError(String cartId) throws CartNotFoundException {
        CartReceiptError receipt;
        try {
            receipt = this.cartReceiptsCosmosClient.getCartReceiptError(cartId);
        } catch (CartNotFoundException e) {
            String errorMsg = String.format("Cart receipt error not found with the cart-id %s", cartId);
            throw new CartNotFoundException(errorMsg, e);
        }

        if (receipt == null) {
            String errorMsg = String.format("Receipt error retrieved with the cart-id %s is null", cartId);
            throw new CartNotFoundException(errorMsg);
        }
        return receipt;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<FeedResponse<CartForReceipt>> getNotNotifiedCartReceiptByStatus(
            String continuationToken,
            Integer pageSize,
            CartStatusType statusType
    ) {
        if (statusType == null) {
            throw new IllegalArgumentException("at least one status must be specified");
        }
        if (statusType.equals(CartStatusType.IO_ERROR_TO_NOTIFY)) {
            return this.cartReceiptsCosmosClient.getIOErrorToNotifyCartReceiptDocuments(continuationToken, pageSize);
        }
        if (statusType.equals(CartStatusType.GENERATED)) {
            return this.cartReceiptsCosmosClient.getGeneratedCartReceiptDocuments(continuationToken, pageSize);
        }
        String errMsg = String.format("Unexpected status for retrieving not notified receipt: %s", statusType);
        throw new IllegalStateException(errMsg);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<FeedResponse<CartForReceipt>> getFailedCartReceiptByStatus(
            String continuationToken,
            Integer pageSize,
            CartStatusType statusType
    ) {
        if (statusType == null) {
            throw new IllegalArgumentException("at least one status must be specified");
        }
        if (statusType.equals(CartStatusType.FAILED) || statusType.equals(CartStatusType.NOT_QUEUE_SENT)) {
            return this.cartReceiptsCosmosClient.getFailedCartReceiptDocuments(continuationToken, pageSize);
        }
        if (statusType.equals(CartStatusType.INSERTED)  || statusType.equals(CartStatusType.WAITING_FOR_BIZ_EVENT)) {
            return this.cartReceiptsCosmosClient.getInsertedCartReceiptDocuments(continuationToken, pageSize);
        }
        String errMsg = String.format("Unexpected status for retrieving failed receipt: %s", statusType);
        throw new IllegalStateException(errMsg);
    }

    @Override
    public CartForReceipt getCart(String cartId) throws CartNotFoundException {
        CartForReceipt cartForReceipt;
        try {
            cartForReceipt = this.cartReceiptsCosmosClient.getCartItem(cartId);
        } catch (CartNotFoundException e) {
            String errorMsg = String.format("Receipt not found with the biz-event id %s", cartId);
            throw new CartNotFoundException(errorMsg, e);
        }

        if (cartForReceipt == null) {
            String errorMsg = String.format("Receipt retrieved with the biz-event id %s is null", cartId);
            throw new CartNotFoundException(errorMsg);
        }
        return cartForReceipt;
    }
}