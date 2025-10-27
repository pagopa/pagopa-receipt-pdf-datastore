package it.gov.pagopa.receipt.pdf.datastore.client;


import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotFoundException;

public interface BizEventCosmosClient {

    BizEvent getBizEventDocument(String bizEventId) throws BizEventNotFoundException;
}
