package it.gov.pagopa.receipt.pdf.datastore;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.UnableToQueueException;
import it.gov.pagopa.receipt.pdf.datastore.utils.ObjectMapperUtils;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class ManageReceiptPoisonQueue {

    /**
     * This function will be invoked when a Queue trigger occurs
     *
     *
     * @param errorMessage payload of the message sent to the poison queue, triggering the function
     * @param documentdb      Output binding that will insert/update data with the errors not to retry within the function
     * @param context         Function context
     */
    @FunctionName("ManageReceiptPoisonQueueProcessor")
    public void processManageReceiptPoisonQueue(
            @QueueTrigger(
                    name = "QueueReceiptWaitingForGenPoison",
                    queueName = "%RECEIPT_QUEUE_TOPIC_POISON%",
                    connection = "RECEIPT_QUEUE_CONN_STRING")
            String errorMessage,
            @CosmosDBOutput(
                    name = "ReceiptMessageErrorsDatastore",
                    databaseName = "db",
                    collectionName = "receipts-message-errors",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<ReceiptError> documentdb,
            final ExecutionContext context) {

        Logger logger = context.getLogger();
        BizEvent bizEvent = null;

        String logMsg = String.format("[ManageReceiptPoisonQueueProcessor] function called at %s for payload %s",
                LocalDateTime.now(), errorMessage);
        logger.info(logMsg);
        boolean retriableContent = false;

        try {
            //attempt to Map queue bizEventMessage to BizEvent
            bizEvent = ObjectMapperUtils.mapString(errorMessage, BizEvent.class);
            logMsg = String.format("ManageReceiptPoisonQueueProcessor function called at %s recognized as valid BizEvent" +
                            "with id %s",
                    LocalDateTime.now(), bizEvent.getId());
            logger.info(logMsg);
            if (bizEvent.getAttemptedPoisonRetry()) {
                logMsg = String.format("ManageReceiptPoisonQueueProcessor function called at %s for event with id %s" +
                                " has ingestion already retried, sending to review",
                        LocalDateTime.now(), bizEvent.getId());
                logger.info(logMsg);
            } else {
                retriableContent = true;
            }
        } catch (JsonProcessingException e) {
            logMsg = String.format("ManageReceiptPoisonQueueProcessor received parsing error in the" +
                    " function called at %s for payload %s", LocalDateTime.now(), errorMessage);
            logger.info(logMsg);
        }

        if (retriableContent) {
            bizEvent.setAttemptedPoisonRetry(true);
            ReceiptQueueClientImpl queueService = ReceiptQueueClientImpl.getInstance();
            try {
                Response<SendMessageResult> sendMessageResult =
                        queueService.sendMessageToQueue(Base64.getMimeEncoder()
                                .encodeToString(Objects.requireNonNull(ObjectMapperUtils.writeValueAsString(bizEvent))
                                        .getBytes()));
                if (sendMessageResult.getStatusCode() != HttpStatus.CREATED.value()) {
                    throw new UnableToQueueException("Unable to queue due to error: " +
                            sendMessageResult.getStatusCode());
                }
            } catch (Exception e) {
                logMsg = String.format("[ManageReceiptPoisonQueueProcessor] error for the function called at %s when attempting" +
                                "to requeue BizEvent wit id %s, saving to cosmos for review. Call Error %s",
                        LocalDateTime.now(), bizEvent.getId(), e.getMessage());
                logger.info(logMsg);
                saveToDocument(errorMessage, documentdb, logger);
            }
        } else {
            saveToDocument(errorMessage, documentdb, logger);
        }

    }

    private void saveToDocument(String errorMessage, OutputBinding<ReceiptError> documentdb, Logger logger) {
        logger.info("[ManageReceiptPoisonQueueProcessor] saving new entry to the retry error to review");
        documentdb.setValue(ReceiptError.builder().messagePayload(errorMessage)
                .status(ReceiptErrorStatusType.TO_REVIEW).build());
    }

}
