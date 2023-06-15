package it.gov.pagopa.receipt.pdf.datastore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.utils.ObjectMapperUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class GenerateReceipt {

    private ReceiptCosmosClient receiptCosmosClient;

    GenerateReceipt(ReceiptCosmosClient receiptCosmosClient){
        this.receiptCosmosClient = receiptCosmosClient;
    }

    GenerateReceipt(){
        this.receiptCosmosClient = new ReceiptCosmosClient();
    }

    @FunctionName("GenerateReceiptProcess")
    @ExponentialBackoffRetry(maxRetryCount = 5, minimumInterval = "500", maximumInterval = "5000")
    public void processGenerateReceipt(
            @QueueTrigger(
                    name = "QueueReceiptWaitingForGen",
                    queueName = "RECEIPT_QUEUE_TOPIC",
                    connection = "RECEIPT_QUEUE_CONN_STRING")
            String message,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    collectionName = "receipts",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<Receipt>> documentdb,
            @QueueOutput(
                    name = "QueueReceiptWaitingForGen",
                    queueName = "RECEIPT_QUEUE_TOPIC",
                    connection = "RECEIPT_QUEUE_CONN_STRING")
            OutputBinding<String> requeueMessage,
            final ExecutionContext context) {

        BizEvent bizEvent = ObjectMapperUtils.map(message, BizEvent.class);

        List<Receipt> itemsToNotify = new ArrayList<>();
        Logger logger = context.getLogger();

        String logMsg = String.format("GenerateReceipt function called at %s", LocalDateTime.now());
        logger.info(logMsg);

        String messageText = "";
        try {
            messageText = ObjectMapperUtils.writeValueAsString(bizEvent);
        } catch (JsonProcessingException err) {
            logger.severe("Error parsing bizEvent as string at " + LocalDateTime.now() + " : " + err.getMessage());
        }

        Receipt receipt = null;
        try{
            receipt = receiptCosmosClient.getReceiptDocument(bizEvent.getId());
        } catch (ReceiptNotFoundException e) {
            requeueMessage.setValue(messageText);
        }


        if (receipt != null && receipt.getStatus().equals(ReceiptStatusType.INSERTED)) {

            logMsg = String.format("GenerateReceipt function called at %s for event with id %s and status %s",
                    LocalDateTime.now(), bizEvent.getId(), bizEvent.getEventStatus());
            logger.info(logMsg);

            //TODO verify if payer and debtor fiscal code are different or not
            String debtorCF = receipt.getEventData().getDebtorFiscalCode();
            String payerCF = receipt.getEventData().getPayerFiscalCode();

            boolean payerDebtorEqual = payerCF.equals(debtorCF);

            for (int i = 0; i < (payerDebtorEqual ? 1 : 2); i++) {
                //TODO verify if there is a pdf already generated (generate only the missing one)
                switch (i) {
                    case 0:
                        if(receipt.getMdAttach().getName().isEmpty()){
                            if(payerDebtorEqual){
                                //TODO generate complete template
                                handleReceiptPDFGeneration(receipt, 1, requeueMessage, messageText, logger);
                            } else {
                                //TODO generate partial template
                                handleReceiptPDFGeneration(receipt, 2, requeueMessage, messageText, logger);
                            }
                        }
                        break;
                    case 1:
                        if (receipt.getMdAttachPayer().getName().isEmpty()) {
                            //TODO generate complete template
                            handleReceiptPDFGeneration(receipt, 1, requeueMessage, messageText, logger);
                        }
                        break;
                }
            }

            itemsToNotify.add(receipt);
        }

        try {
            //TODO Update receipt and save PDF(s) on BLOB Storage
            documentdb.setValue(itemsToNotify);
        } catch (NullPointerException e) {
            requeueMessage.setValue(messageText);
            logger.severe("NullPointerException exception on cosmos receipts msg ingestion at " + LocalDateTime.now() + " : " + e.getMessage());
        } catch (Exception e) {
            requeueMessage.setValue(messageText);
            logger.severe("Generic exception on cosmos receipts msg ingestion at " + LocalDateTime.now() + " : " + e.getMessage());
        }
    }

    private void handleReceiptPDFGeneration(Receipt receipt, int templateType, OutputBinding<String> requeueMessage, String messageText, Logger logger){
        int pdfEngineStatusCode = 0;

            //TODO add call to PDFEngine service to generate pdf (full o partial template)

        if(pdfEngineStatusCode == 200){
            //TODO update receipt with PDF metadata
            receipt.setStatus(ReceiptStatusType.GENERATED);
        } else {
            //TODO menage max number of retry -> then FAILED
            if(receipt.getNumRetry() > 5){
                receipt.setStatus(ReceiptStatusType.FAILED);
            } else {
                receipt.setStatus(ReceiptStatusType.RETRY);
            }
            //TODO menage api call error code -> pdfengineStatusCode + add PDFEngine error message
            ReasonError reasonError = new ReasonError(ReasonErrorCode.ERROR_PDF_ENGINE.getCustomCode(pdfEngineStatusCode), "Error generating PDF: " );
            receipt.setReasonErr(reasonError);
            receipt.setNumRetry(receipt.getNumRetry() + 1);

            requeueMessage.setValue(messageText);
            //TODO add PDFEngine error message
            logger.severe("Error generating PDF at " + LocalDateTime.now() + " : ");
        }




    }
}
