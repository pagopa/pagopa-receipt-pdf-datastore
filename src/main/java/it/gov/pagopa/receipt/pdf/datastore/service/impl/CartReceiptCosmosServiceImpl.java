package it.gov.pagopa.receipt.pdf.datastore.service.impl;

import it.gov.pagopa.receipt.pdf.datastore.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.CartReceiptsCosmosClientImpl;
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
}