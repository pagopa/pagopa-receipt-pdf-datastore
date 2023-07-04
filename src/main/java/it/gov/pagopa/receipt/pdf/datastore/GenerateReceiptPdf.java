package it.gov.pagopa.receipt.pdf.datastore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotValidException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.datastore.service.GenerateReceiptPdfService;
import it.gov.pagopa.receipt.pdf.datastore.utils.ObjectMapperUtils;
import org.apache.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class GenerateReceiptPdf {

    /**
     * This function will be invoked when a Queue trigger occurs
     * #
     * The biz-event is mapped from the string to the BizEvent object
     * The receipt's data is retrieved from CosmosDB by the biz-event's id
     * If receipt has status INSERTED or RETRY
     * Is verified if the debtor's and payer's fiscal code are the same
     * If different it will generate a pdf for each:
     * - Complete template for the payer
     * - Partial template for the debtor
     * If the fiscal code is the same it will generate only one pdf with the complete template
     * For every pdf to generate:
     * - call the API to the PDF Engine to generate the file from the template
     * - the pdf is saved to the designed Azure Blob Storage
     * - the pdf metadata retrieved from the storage are saved on the receipt's data (file name & url)
     * If everything succeeded the receipt's status will be updated to GENERATED and saved to CosmosDB
     * #
     * The bizEventMessage is re-sent to the queue in case of errors like:
     * - there is an error generating at least one pdf;
     * - there is an error saving at least one pdf to blob storage;
     * - errors processing the data;
     * #
     * The receipt is discarded in case of:
     * - the receipt is null
     * - the receipt has not valid event data
     * - the receipt's status is not INSERTED or RETRY
     * #
     * After too many retry the receipt's status will be updated to FAILED
     *
     * @param bizEventMessage BizEventMessage, with biz-event's data, triggering the function
     * @param documentdb      Output binding that will update the receipt data with the pdfs metadata
     * @param requeueMessage  Output binding that will re-send the bizEventMessage to the queue in case of errors
     * @param context         Function context
     */
    @FunctionName("GenerateReceiptProcess")
    //Deprecated annotation for queue function, to be managed through host.json,
    // see https://learn.microsoft.com/en-us/azure/azure-functions/functions-bindings-storage-queue?tabs=in-process%2Cextensionv5%2Cextensionv3&pivots=programming-language-java#host-json
    //@ExponentialBackoffRetry(maxRetryCount = 5, minimumInterval = "500", maximumInterval = "5000")
    public void processGenerateReceipt(
            @QueueTrigger(
                    name = "QueueReceiptWaitingForGen",
                    queueName = "%RECEIPT_QUEUE_TOPIC%",
                    connection = "RECEIPT_QUEUE_CONN_STRING")
            String bizEventMessage,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    collectionName = "receipts",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<Receipt>> documentdb,
            @QueueOutput(
                    name = "QueueReceiptWaitingForGenOutput",
                    queueName = "%RECEIPT_QUEUE_TOPIC%",
                    connection = "RECEIPT_QUEUE_CONN_STRING")
            OutputBinding<String> requeueMessage,
            final ExecutionContext context) throws BizEventNotValidException, ReceiptNotFoundException {

        //Map queue bizEventMessage to BizEvent
        BizEvent bizEvent;

        try {
            bizEvent = ObjectMapperUtils.mapString(bizEventMessage, BizEvent.class);
        } catch (JsonProcessingException e) {
            throw new BizEventNotValidException("Error parsing the message coming from the queue", e);
        }

        List<Receipt> itemsToNotify = new ArrayList<>();
        Logger logger = context.getLogger();

        String logMsg = String.format("GenerateReceipt function called at %s", LocalDateTime.now());
        logger.info(logMsg);

        //Retrieve receipt's data from CosmosDB
        Receipt receipt;

        ReceiptCosmosClientImpl receiptCosmosClient = ReceiptCosmosClientImpl.getInstance();

        //Retrieve receipt from CosmosDB
        try {
            receipt = receiptCosmosClient.getReceiptDocument(bizEvent.getId());
        } catch (ReceiptNotFoundException e) {
            String errorMsg = String.format("Receipt not found with the biz-event id %s", bizEvent.getId());
            throw new ReceiptNotFoundException(errorMsg, e);
        }

        int discarder = 0;
        int numberOfSavedPdfs = 0;

        //Verify receipt status
        if (receipt != null &&
                receipt.getEventData() != null &&
                (receipt.getStatus().equals(ReceiptStatusType.INSERTED) ||
                        receipt.getStatus().equals(ReceiptStatusType.RETRY))
        ) {

            GenerateReceiptPdfService service = new GenerateReceiptPdfService();

            logMsg = String.format("GenerateReceipt function called at %s for event with id %s and status %s",
                    LocalDateTime.now(), bizEvent.getId(), bizEvent.getEventStatus());
            logger.info(logMsg);

            //Verify if debtor's and payer's fiscal code are the same
            String debtorCF = receipt.getEventData().getDebtorFiscalCode();
            String payerCF = receipt.getEventData().getPayerFiscalCode();

            if (debtorCF != null || payerCF != null) {
                boolean generateOnlyDebtor = payerCF == null || payerCF.equals(debtorCF);

                //Generate and save PDF
                PdfGeneration pdfGeneration = service.handlePdfsGeneration(generateOnlyDebtor, receipt, bizEvent, debtorCF, payerCF, logger);

                //Write PDF blob storage metadata on receipt
                numberOfSavedPdfs = service.addPdfsMetadataToReceipt(receipt, pdfGeneration);

                //Verify PDF generation success
                service.verifyPdfGeneration(bizEventMessage, requeueMessage, logger, receipt, generateOnlyDebtor, pdfGeneration);


            } else {
                String errorMessage = String.format(
                        "Error processing receipt with id %s : both debtor's and payer's fiscal code are null",
                        receipt.getId()
                );

                service.handleErrorGeneratingReceipt(
                        ReceiptStatusType.FAILED,
                        HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        errorMessage,
                        bizEventMessage,
                        receipt,
                        requeueMessage,
                        logger
                );
            }

            //Add receipt to items to be saved to CosmosDB
            itemsToNotify.add(receipt);

        } else {
            discarder++;
        }

        //Discarder info
        logMsg = String.format("itemsDone stat %s function - %d number of events in discarder  ", context.getInvocationId(), discarder);
        logger.info(logMsg);

        //Call to blob storage info
        logMsg = String.format("itemsDone stat %s function - number of PDFs sent to the receipt blob storage %d", context.getInvocationId(), numberOfSavedPdfs);
        logger.info(logMsg);

        //Call to datastore info
        logMsg = String.format("GenerateReceiptProcess stat %s function - number of receipt inserted on the datastore %d", context.getInvocationId(), itemsToNotify.size());
        logger.info(logMsg);

        if (!itemsToNotify.isEmpty()) {
            documentdb.setValue(itemsToNotify);
        }
    }
}
