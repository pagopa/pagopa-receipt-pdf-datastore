package it.gov.pagopa.receipt.pdf.datastore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotValidException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.datastore.service.GenerateReceiptPdfService;
import it.gov.pagopa.receipt.pdf.datastore.utils.ObjectMapperUtils;
import org.apache.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
     * @param requeueMessage  Output binding that will re-send the messages considered valid for retry
     * @param context         Function context
     */
    @FunctionName("ManageReceiptPoisonQueueProcessor")
    public void processManageReceiptPoisonQueue(
            @QueueTrigger(
                    name = "QueueReceiptWaitingForGenPoison",
                    queueName = "%RECEIPT_QUEUE_TOPIC-POISON%",
                    connection = "RECEIPT_QUEUE_CONN_STRING")
            String errorMessage,
            @CosmosDBOutput(
                    name = "ReceiptMessageErrorsDatastore",
                    databaseName = "db",
                    collectionName = "receipts-message-errors",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<ReceiptError>> documentdb,
            @QueueOutput(
                    name = "QueueReceiptWaitingForGenOutput",
                    queueName = "%RECEIPT_QUEUE_TOPIC%",
                    connection = "RECEIPT_QUEUE_CONN_STRING")
            OutputBinding<String> requeueMessage,
            final ExecutionContext context) throws BizEventNotValidException, ReceiptNotFoundException {

        Logger logger = context.getLogger();
        BizEvent bizEvent = null;

        String logMsg = String.format("ManageReceiptPoisonQueueProcessor function called at %s for payload %s",
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
            requeueMessage.setValue(ObjectMapperUtils.writeValueAsString(bizEvent));
        } else {
            documentdb.setValue(Collections.singletonList(ReceiptError.builder().messagePayload(errorMessage)
                    .status(ReceiptErrorStatusType.TO_REVIEW).build()));
        }

    }
}
