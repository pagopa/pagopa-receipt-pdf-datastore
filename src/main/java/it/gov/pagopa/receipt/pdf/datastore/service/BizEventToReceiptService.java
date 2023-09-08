package it.gov.pagopa.receipt.pdf.datastore.service;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.utils.ObjectMapperUtils;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;

import java.util.Base64;
import java.util.Objects;

@NoArgsConstructor
public class BizEventToReceiptService {

    /**
     * Handles sending biz-events as message to queue and updates receipt's status
     *
     * @param bizEvent Biz-event from CosmosDB
     * @param receipt  Receipt to update
     */
    public void handleSendMessageToQueue(BizEvent bizEvent, Receipt receipt, Logger logger) {
        //Encode biz-event to base64 string
        String messageText = Base64.getMimeEncoder().encodeToString(Objects.requireNonNull(ObjectMapperUtils.writeValueAsString(bizEvent)).getBytes());

        ReceiptQueueClientImpl queueService = ReceiptQueueClientImpl.getInstance();

        //Add message to the queue
        try {
            Response<SendMessageResult> sendMessageResult = queueService.sendMessageToQueue(messageText);

            if (sendMessageResult.getStatusCode() == HttpStatus.CREATED.value()) {
                receipt.setStatus(ReceiptStatusType.INSERTED);
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
}
