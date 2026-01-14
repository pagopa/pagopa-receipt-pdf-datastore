package it.gov.pagopa.receipt.pdf.datastore.service;

import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.CartReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;

/**
 * Service that handle the input and output for the {@link ReceiptCosmosClient}
 */
public interface CartReceiptCosmosService {

    /**
     * Retrieve the cart receipt error with the provided cart-id
     *
     * @param cartId the cart id
     * @return the receipt error
     * @throws CartNotFoundException if the receipt was not found or the retrieved receipt is null
     */
    CartReceiptError getCartReceiptError(String cartId) throws CartNotFoundException;

    /**
     * Retrieve the not notified cart receipt with the provided {@link CartStatusType} status
     *
     * @param continuationToken Paged query continuation token
     * @param pageSize the page size
     * @param statusType the status of the cart receipts
     * @return cart receipt documents
     */
    Iterable<FeedResponse<CartForReceipt>> getNotNotifiedCartReceiptByStatus(
            String continuationToken,
            Integer pageSize,
            CartStatusType statusType
    );

    /**
     * Retrieve the failed cart receipt with the provided {@link CartStatusType} status
     *
     * @param continuationToken Paged query continuation token
     * @param pageSize          the page size
     * @param statusType        the status of the receipts
     * @return receipt documents
     */
    Iterable<FeedResponse<CartForReceipt>> getFailedCartReceiptByStatus(
            String continuationToken,
            Integer pageSize,
            CartStatusType statusType
    );


    /**
     * Retrieve the cart with the provided id
     *
     * @param cartId the cart id
     * @return the cart
     * @throws CartNotFoundException if the cart was not found or the retrieved cart is null
     */
    CartForReceipt getCart(String cartId) throws CartNotFoundException;
}
