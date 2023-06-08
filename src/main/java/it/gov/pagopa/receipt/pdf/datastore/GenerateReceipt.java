package it.gov.pagopa.receipt.pdf.datastore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.receipt.pdf.datastore.entities.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entities.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entities.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entities.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entities.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.datastore.entities.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.utils.ObjectMapperUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class GenerateReceipt {

    @FunctionName("GenerateReceiptProcess")
    @ExponentialBackoffRetry(maxRetryCount = 5, minimumInterval = "500", maximumInterval = "5000")
    public void processGenerateReceipt(
            @QueueTrigger(
                    name = "QueueReceiptWaitingForGen",
                    queueName = "COSMOS_RECEIPT_QUEUE_TOPIC",
                    connection = "COSMOS_RECEIPT_QUEUE_CONN_STRING")
            String message,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    collectionName = "receipts",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<Receipt>> documentdb,
            @QueueOutput(
                    name = "QueueReceiptWaitingForGen",
                    queueName = "COSMOS_RECEIPT_QUEUE_TOPIC",
                    connection = "COSMOS_RECEIPT_QUEUE_CONN_STRING")
            OutputBinding<String> requeueMessage,
            final ExecutionContext context) {

        BizEvent bizEvent = ObjectMapperUtils.map(message, BizEvent.class);

        List<Receipt> itemsDone = new ArrayList<>();
        List<BizEvent> itemsToNotify = new ArrayList<>();
        Logger logger = context.getLogger();

        String logMsg = String.format("GenerateReceipt function called at %s", LocalDateTime.now());
        logger.info(logMsg);

        String messageText = "";
        try {
            messageText = ObjectMapperUtils.writeValueAsString(bizEvent);
        } catch (JsonProcessingException err) {
            logger.severe("Error parsing bizEvent as string at " + LocalDateTime.now() + " : " + err.getMessage());
        }

        //TODO retrieve and update receipt by event id
        Receipt receipt = new Receipt();

        if (receipt.getStatus().equals(ReceiptStatusType.INSERTED)) {

            logMsg = String.format("GenerateReceipt function called at %s for event with id %s and status %s",
                    LocalDateTime.now(), bizEvent.getId(), bizEvent.getEventStatus());
            logger.info(logMsg);

            int pdfEngineStatusCode = 0;

            //TODO verify if payer and debtor fiscal code are different or not

            //TODO verify if there is a pdf already generated (generate only the missing one)

            try {
                //TODO add call to PDFEngine service to generate pdf (full o partial template)

                //TODO update receipt with PDF metadata
                receipt.setStatus(ReceiptStatusType.GENERATED);

            } catch (Exception e) {
                receipt.setStatus(ReceiptStatusType.NOT_QUEUE_SENT);
                //TODO menage api call error code -> pdfengineStatusCode
                ReasonError reasonError = new ReasonError(ReasonErrorCode.ERROR_PDF_ENGINE.getCustomCode(pdfEngineStatusCode), "Error generating PDF: " + e.getMessage());
                receipt.setReasonErr(reasonError);
                receipt.setNumRetry(receipt.getNumRetry()+1);

                requeueMessage.setValue(messageText);

                logger.severe("Error generating PDF at " + LocalDateTime.now() + " : " + e.getMessage());
            }

            itemsDone.add(receipt);
            itemsToNotify.add(bizEvent);
        }

        try {
            //TODO Update receipt and save PDF(s) on BLOB Storage
            documentdb.setValue(itemsDone);
        } catch (NullPointerException e) {
            requeueMessage.setValue(messageText);
            logger.severe("NullPointerException exception on cosmos receipts msg ingestion at " + LocalDateTime.now() + " : " + e.getMessage());
        } catch (Exception e) {
            requeueMessage.setValue(messageText);
            logger.severe("Generic exception on cosmos receipts msg ingestion at " + LocalDateTime.now() + " : " + e.getMessage());
        }
    }
}
