package it.gov.pagopa.receipt.pdf.datastore.client;


import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotFoundException;

import java.util.List;

public interface BizEventCosmosClient {

    /**
     * @param bizEventId the id of the bizEvent to retrieve
     * @return the BizEvent document from Cosmos DB
     * @throws BizEventNotFoundException if the BizEvent with the given id is not found
     *                                   <p>
     *                                   This method retrieves a BizEvent document from Cosmos DB using the provided bizEventId.
     */
    BizEvent getBizEventDocument(String bizEventId) throws BizEventNotFoundException;

    /**
     * Retrieve all biz-event documents related to a specific cart from CosmosDB database
     *
     * @param transactionId id that identifies the cart
     * @return a list of biz-event document
     */
    List<BizEvent> getAllCartBizEventDocument(String transactionId);
}
