package it.gov.pagopa.receipt.pdf.datastore.client;

import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;

public interface BizEventCosmosClient {
    BizEvent getBizEventDocument(String eventId) throws ReceiptNotFoundException;
}
