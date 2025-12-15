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

    Iterable<FeedResponse<Receipt>> getFailedReceiptDocuments(String continuationToken, Integer pageSize);

    CosmosItemResponse<Receipt> saveReceipts(Receipt receipt);

    ReceiptError getReceiptError(String bizEventId) throws  ReceiptNotFoundException;

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

    IOMessage getIoMessage(String messageId) throws IoMessageNotFoundException;

}
