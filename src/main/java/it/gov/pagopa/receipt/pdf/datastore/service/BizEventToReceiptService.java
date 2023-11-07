package it.gov.pagopa.receipt.pdf.datastore.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;

public interface BizEventToReceiptService {

    void handleSendMessageToQueue(BizEvent bizEvent, Receipt receipt);

    void handleError(Receipt receipt);

    String getTransactionCreationDate(BizEvent bizEvent);

    void tokenizeFiscalCodes(BizEvent bizEvent, Receipt receipt, EventData eventData)  throws JsonProcessingException, PDVTokenizerException;
}
