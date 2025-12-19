package it.gov.pagopa.receipt.pdf.datastore.client;

import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.IOMessage;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.IoMessageNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;

public interface ReceiptCosmosClient {

    /**
     * Retrieve receipt document from CosmosDB database
     *
     * @param eventId Biz-event id
     * @return receipt document
     * @throws ReceiptNotFoundException in case no receipt has been found with the given idEvent
     */
    Receipt getReceiptDocument(String eventId) throws ReceiptNotFoundException;

    /**
     * Retrieve receiptError document from CosmosDB database
     *
     * @param bizEventId BizEvent ID
     * @return ReceiptError found
     * @throws ReceiptNotFoundException If the document isn't found
     */
    ReceiptError getReceiptError(String bizEventId) throws  ReceiptNotFoundException;

    /**
     * Retrieve failed receipt documents from CosmosDB database
     *
     * @param continuationToken Paged query continuation token
     * @return receipt documents
     */
    Iterable<FeedResponse<Receipt>> getFailedReceiptDocuments(String continuationToken, Integer pageSize);

    /**
     * Save Receipts on CosmosDB database
     *
     * @param receipt Receipts to save
     * @return receipt documents
     */
    CosmosItemResponse<Receipt> saveReceipts(Receipt receipt);

    /**
     * Retrieve the not notified receipt documents with {@link ReceiptStatusType#GENERATED}
     *
     * @param continuationToken Paged query continuation token
     * @param pageSize the page size
     * @return receipt documents
     */
    Iterable<FeedResponse<Receipt>> getGeneratedReceiptDocuments(String continuationToken, Integer pageSize);

    /**
     * Retrieve the receipt not notified documents with {@link ReceiptStatusType#IO_ERROR_TO_NOTIFY}
     *
     * @param continuationToken Paged query continuation token
     * @param pageSize the page size
     * @return receipt documents
     */
    Iterable<FeedResponse<Receipt>> getIOErrorToNotifyReceiptDocuments(String continuationToken, Integer pageSize);

    /**
     * Retrieve the failed receipt documents with {@link ReceiptStatusType#INSERTED} status
     *
     * @param continuationToken Paged query continuation token
     * @param pageSize the page size
     * @return receipt documents
     */
    Iterable<FeedResponse<Receipt>> getInsertedReceiptDocuments(String continuationToken, Integer pageSize);

    /**
     * Retrieve receipt document from CosmosDB database
     *
     * @param messageId IO Message id
     * @return io message document
     * @throws IoMessageNotFoundException in case no receipt has been found with the given messageId
     */
    IOMessage getIoMessage(String messageId) throws IoMessageNotFoundException, IoMessageNotFoundException;

}
