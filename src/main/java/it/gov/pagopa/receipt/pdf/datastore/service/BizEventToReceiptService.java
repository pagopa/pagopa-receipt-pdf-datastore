package it.gov.pagopa.receipt.pdf.datastore.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.Payload;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;

import java.util.List;

public interface BizEventToReceiptService {

    /**
     * Handles sending biz-events as message to queue and updates receipt's status
     *
     * @param bizEvent Biz-event from CosmosDB
     * @param receipt  Receipt to update
     */
    void handleSendMessageToQueue(BizEvent bizEvent, Receipt receipt);

    /**
     * This method handles sending biz-events as messages to the cart queue.
     *
     * @param bizEventList   the list of biz-events to send to the cart queue
     * @param cartForReceipt the cart associated with the biz-events.
     *                       The status of this object may be updated if there are errors during the process.
     */
    void handleSendCartMessageToQueue(List<BizEvent> bizEventList, CartForReceipt cartForReceipt);

    /**
     * Recovers a receipt from the CosmosDB by the property eventId
     *
     * @param bizEventId BizEvent id relative to the receipt
     * @return the receipt found
     * @throws ReceiptNotFoundException when no receipt has been found
     */
    Receipt getReceipt(String bizEventId) throws ReceiptNotFoundException;

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
     * @param bizEvent  BizEvent where fiscalCodes are stored
     * @param receipt   Receipt to update in case of errors
     * @param eventData Event data to update with tokenized fiscalCodes
     * @throws JsonProcessingException if an error occur when parsing input or output
     * @throws PDVTokenizerException   if an error occur when invoking the PDV Tokenizer
     */
    void tokenizeFiscalCodes(
            BizEvent bizEvent,
            Receipt receipt,
            EventData eventData
    ) throws JsonProcessingException, PDVTokenizerException;

    /**
     * Search for a cart associated with the provided transaction id, if present
     * it updates the cart section {@link Payload#getCart()} with the biz-event info
     * otherwise it creates a new {@link CartForReceipt} with the biz-event info
     *
     * @param bizEvent the biz-event
     * @return the cart with the biz-event info
     */
    CartForReceipt buildCartForReceipt(BizEvent bizEvent);

    /**
     * Retrieve all events that are associated to the cart with the specified id
     *
     * @param cart the cart
     * @return a list of biz-events
     */
    List<BizEvent> getCartBizEvents(CartForReceipt cart);

    /**
     * This method saves the provided CartForReceipt object to the datastore.
     *
     * <p>
     * If the operation fail for concurrent update, it tries to rebuild a cart object
     * by invoking {@link #buildCartForReceipt(BizEvent)} with the provided biz event
     * and then saves it on Cosmos.
     * If the operation fail again or with another error it change the {@link CartForReceipt#getStatus()}
     * to {@link CartStatusType#FAILED} and add a {@link ReasonError}
     * </p>
     *
     * @param cartForReceipt the cart to save
     * @param bizEvent       the biz event use to recreate the cart
     * @return the saved cart or if it fails the cart updated with the reason error
     */
    CartForReceipt saveCartForReceipt(CartForReceipt cartForReceipt, BizEvent bizEvent);

    /**
     * Recovers a cart from the CosmosDB by the property eventId
     *
     * @param cartId the cart identifier
     * @return the cart found
     * @throws CartNotFoundException when no cart has been found
     */
    CartForReceipt getCartForReceipt(String cartId) throws CartNotFoundException;
}
