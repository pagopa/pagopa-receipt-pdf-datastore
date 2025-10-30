package it.gov.pagopa.receipt.pdf.datastore.client;

import com.azure.cosmos.models.CosmosItemResponse;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartConcurrentUpdateException;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;

public interface CartReceiptsCosmosClient {

    /**
     * Retrieve Cart For Receipt document from CosmosDB database
     *
     * @param eventId Biz-event transaction id, that identifies the cart
     * @return cart-for-receipts document
     * @throws CartNotFoundException in case no cart has been found with the given event id
     */
    CartForReceipt getCartItem(String eventId) throws CartNotFoundException;

    /**
     * Save Cart For Receipt on CosmosDB database
     *
     * @param receipt Cart Data to save
     * @return the saved cart-for-receipts document
     */
    CosmosItemResponse<CartForReceipt> saveCart(CartForReceipt receipt);

    /**
     * Update Cart For Receipt on CosmosDB database
     *
     * @param receipt Cart Data to save
     * @return the updated cart-for-receipts document
     */
    CosmosItemResponse<CartForReceipt> updateCart(CartForReceipt receipt) throws CartConcurrentUpdateException;
}
