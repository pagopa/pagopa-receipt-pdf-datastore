package it.gov.pagopa.receipt.pdf.datastore.client;

import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.CartReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartConcurrentUpdateException;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;

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
     * Update Cart For Receipt on CosmosDB database
     *
     * @param receipt Cart Data to save
     * @return the updated cart-for-receipts document
     */
    CosmosItemResponse<CartForReceipt> updateCart(CartForReceipt receipt) throws CartConcurrentUpdateException;

    /**
     * Retrieve cartReceiptError document from CosmosDB database
     *
     * @param cartId id of the collection
     * @return CartReceiptError found
     * @throws CartNotFoundException If the document isn't found
     */
    CartReceiptError getCartReceiptError(String cartId) throws  CartNotFoundException;

    /**
     * Retrieve failed cart receipt documents from CosmosDB database
     *
     * @param continuationToken Paged query continuation token
     * @return receipt documents
     */
    Iterable<FeedResponse<CartForReceipt>> getFailedCartReceiptDocuments(String continuationToken, Integer pageSize);

    /**
     * Retrieve the failed cart  receipt documents with {@link CartStatusType#INSERTED} status
     *
     * @param continuationToken Paged query continuation token
     * @param pageSize the page size
     * @return receipt documents
     */
    Iterable<FeedResponse<CartForReceipt>> getInsertedCartReceiptDocuments(String continuationToken, Integer pageSize);

    /**
     * Retrieve the not notified cart receipt documents with {@link CartStatusType#IO_ERROR_TO_NOTIFY}
     *
     * @param continuationToken Paged query continuation token
     * @param pageSize the page size
     * @return cart receipt documents
     */
    Iterable<FeedResponse<CartForReceipt>> getIOErrorToNotifyCartReceiptDocuments(String continuationToken, Integer pageSize);

    /**
     * Retrieve the not notified cart receipt documents with {@link CartStatusType#GENERATED}
     *
     * @param continuationToken Paged query continuation token
     * @param pageSize the page size
     * @return cart receipt documents
     */
    Iterable<FeedResponse<CartForReceipt>> getGeneratedCartReceiptDocuments(String continuationToken, Integer pageSize);
}
