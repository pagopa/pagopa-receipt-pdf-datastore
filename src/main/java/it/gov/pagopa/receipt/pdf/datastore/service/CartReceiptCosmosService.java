package it.gov.pagopa.receipt.pdf.datastore.service;

import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptCosmosClient;
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

}
