package it.gov.pagopa.receipt.pdf.datastore.service.impl;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.datastore.service.PDVTokenizerService;
import it.gov.pagopa.receipt.pdf.datastore.utils.ObjectMapperUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Objects;

public class BizEventToReceiptServiceImpl implements BizEventToReceiptService {

    private final Logger logger = LoggerFactory.getLogger(BizEventToReceiptServiceImpl.class);

    private final PDVTokenizerService pdvTokenizerService;

    public BizEventToReceiptServiceImpl() {
        this.pdvTokenizerService = new PDVTokenizerServiceImpl();
    }

    public BizEventToReceiptServiceImpl(PDVTokenizerService pdvTokenizerService) {
        this.pdvTokenizerService = pdvTokenizerService;
    }

    /**
     * Handles sending biz-events as message to queue and updates receipt's status
     *
     * @param bizEvent Biz-event from CosmosDB
     * @param receipt  Receipt to update
     */
    public void handleSendMessageToQueue(BizEvent bizEvent, Receipt receipt) {
        //Encode biz-event to base64 string
        String messageText = Base64.getMimeEncoder().encodeToString(Objects.requireNonNull(ObjectMapperUtils.writeValueAsString(bizEvent)).getBytes());

        ReceiptQueueClientImpl queueService = ReceiptQueueClientImpl.getInstance();

        //Add message to the queue
        try {
            Response<SendMessageResult> sendMessageResult = queueService.sendMessageToQueue(messageText);

            if (sendMessageResult.getStatusCode() == HttpStatus.CREATED.value()) {
                receipt.setStatus(ReceiptStatusType.INSERTED);
                receipt.setInserted_at(System.currentTimeMillis());
            } else {
                handleError(receipt);
            }
        } catch (Exception e) {
            handleError(receipt);
            //Error info
            logger.error("Error sending to queue biz-event message with id {}", bizEvent.getId(), e);
        }
    }

    /**
     * Handles errors for queue and updates receipt's status accordingly
     *
     * @param receipt Receipt to update
     */
    public void handleError(Receipt receipt) {
        receipt.setStatus(ReceiptStatusType.NOT_QUEUE_SENT);
        ReasonError reasonError = new ReasonError(ReasonErrorCode.ERROR_QUEUE.getCode(),
                String.format("[BizEventToReceiptService] Error sending message to queue" +
                        " for receipt with eventId %s", receipt.getEventId()));
        receipt.setReasonErr(reasonError);
    }

    /**
     * Retrieve conditionally the transaction creation date from biz-event
     *
     * @param bizEvent Biz-event from CosmosDB
     * @return transaction date
     */
    public String getTransactionCreationDate(BizEvent bizEvent) {
        if (bizEvent.getTransactionDetails() != null && bizEvent.getTransactionDetails().getTransaction() != null) {
            return bizEvent.getTransactionDetails().getTransaction().getCreationDate();

        } else if (bizEvent.getPaymentInfo() != null) {
            return bizEvent.getPaymentInfo().getPaymentDateTime();
        }

        return null;
    }

    public void tokenizeFiscalCodes(BizEvent bizEvent, Receipt receipt, EventData eventData) throws PDVTokenizerException {
        PDVTokenizerException exception = null;

        try {
            if (bizEvent.getDebtor() != null && bizEvent.getDebtor().getEntityUniqueIdentifierValue() != null) {
                eventData.setDebtorFiscalCode(
                        pdvTokenizerService.generateTokenForFiscalCode(bizEvent.getDebtor().getEntityUniqueIdentifierValue())
                );
            }
            if (bizEvent.getPayer() != null && bizEvent.getPayer().getEntityUniqueIdentifierValue() != null) {
                eventData.setPayerFiscalCode(
                        pdvTokenizerService.generateTokenForFiscalCode(bizEvent.getPayer().getEntityUniqueIdentifierValue())
                );
            }
        } catch (PDVTokenizerException e) {
            exception = new PDVTokenizerException(e.getMessage(), e.getStatusCode());
        } catch (JsonProcessingException e){
            exception = new PDVTokenizerException(e.getMessage(), ReasonErrorCode.ERROR_PDV_TOKENIZER.getCode());
        }

        if(exception != null){
            receipt.setStatus(ReceiptStatusType.FAILED);

            ReasonError reasonError = new ReasonError(exception.getStatusCode(), exception.getMessage());
            receipt.setReasonErr(reasonError);

            logger.error("Error tokenizing receipt with bizEventId {}", bizEvent.getId(), exception);
            throw exception;
        }
    }
}
