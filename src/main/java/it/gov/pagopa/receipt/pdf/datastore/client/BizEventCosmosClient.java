package it.gov.pagopa.receipt.pdf.datastore.client;


import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;

public interface BizEventCosmosClient {

    /**
     * Retrieve all biz-event documents related to a specific cart from CosmosDB database
     *
     * @param transactionId     id that identifies the cart
     * @param continuationToken Paged query continuation token
     * @param pageSize          the page size
     * @return a list of biz-event document
     */
    Iterable<FeedResponse<BizEvent>> getAllBizEventDocument(String transactionId, String continuationToken, Integer pageSize);
}
