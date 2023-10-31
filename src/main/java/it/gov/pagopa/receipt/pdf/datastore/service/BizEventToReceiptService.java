package it.gov.pagopa.receipt.pdf.datastore.service;

import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;

public interface BizEventToReceiptService {

    void handleSendMessageToQueue(BizEvent bizEvent, Receipt receipt);

    void handleError(Receipt receipt);

    String getTransactionCreationDate(BizEvent bizEvent);
}
