package it.gov.pagopa.receipt.pdf.datastore.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptCosmosClient;

public interface BizEventToReceiptService {

    /**
     * Handles sending biz-events as message to queue and updates receipt's status
     *
     * @param bizEvent Biz-event from CosmosDB
     * @param receipt  Receipt to update
     */
    void handleSendMessageToQueue(BizEvent bizEvent, Receipt receipt);

    /**
     * Saves receipts on CosmosDB using {@link ReceiptCosmosClient}
     *
     * @param receipt Receipt to save
     */
    void handleSaveReceipt(Receipt receipt);

    /**
     * Retrieve conditionally the transaction creation date from biz-event
     *
     * @param bizEvent Biz-event from CosmosDB
     * @return transaction date
     */
    String getTransactionCreationDate(BizEvent bizEvent);

    /**
     * Calls PDVTokenizerService to tokenize the fiscal codes for both Debtor & Payer (if present)
     *
     * @param bizEvent BizEvent where fiscalCodes are stored
     * @param receipt Receipt to update in case of errors
     * @param eventData Event data to update with tokenized fiscalCodes
     * @throws JsonProcessingException if an error occur when parsing input or output
     * @throws PDVTokenizerException if an error occur when invoking the PDV Tokenizer
     */
    void tokenizeFiscalCodes(BizEvent bizEvent, Receipt receipt, EventData eventData)  throws JsonProcessingException, PDVTokenizerException;

    /**
     * TODO
     * @param bizEvent
     */
    void handleSaveCart(BizEvent bizEvent);
}
