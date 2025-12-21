package it.gov.pagopa.receipt.pdf.datastore.service;

import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.IOMessage;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.IoMessageNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;

/**
 * Service that handle the input and output for the {@link ReceiptCosmosClient}
 */
public interface ReceiptCosmosService {

    /**
     * Retrieve the receipt with the provided biz-event id
     *
     * @param eventId the biz-event id
     * @return the receipt
     * @throws ReceiptNotFoundException if the receipt was not found or the retrieved receipt is null
     */
    Receipt getReceipt(String eventId) throws ReceiptNotFoundException;

    /**
     * Retrieve the receipt error with the provided biz-event id
     *
     * @param eventId the biz-event id
     * @return the receipt error
     * @throws ReceiptNotFoundException if the receipt was not found or the retrieved receipt is null
     */
    ReceiptError getReceiptError(String eventId) throws ReceiptNotFoundException;

    /**
     * Retrieve the not notified receipt with the provided {@link ReceiptStatusType} status
     *
     * @param continuationToken Paged query continuation token
     * @param pageSize the page size
     * @param statusType the status of the receipts
     * @return receipt documents
     */
    Iterable<FeedResponse<Receipt>> getNotNotifiedReceiptByStatus(
            String continuationToken,
            Integer pageSize,
            ReceiptStatusType statusType
    );

    /**
     * Retrieve the failed receipt with the provided {@link ReceiptStatusType} status
     *
     * @param continuationToken Paged query continuation token
     * @param pageSize the page size
     * @param statusType the status of the receipts
     * @return receipt documents
     */
    Iterable<FeedResponse<Receipt>> getFailedReceiptByStatus(
            String continuationToken,
            Integer pageSize,
            ReceiptStatusType statusType
    );

    /**
     *
     * @param messageId
     * @return
     */
    IOMessage getReceiptMessage(String messageId) throws IoMessageNotFoundException;


    /**
     * Retrieve the cart with the provided id
     *
     * @param cartId the cart id
     * @return the cart
     * @throws CartNotFoundException if the cart was not found or the retrieved cart is null
     */
    CartForReceipt getCart(String cartId) throws CartNotFoundException;
}
