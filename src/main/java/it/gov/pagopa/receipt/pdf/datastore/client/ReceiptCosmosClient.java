package it.gov.pagopa.receipt.pdf.datastore.client;

import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
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
}
