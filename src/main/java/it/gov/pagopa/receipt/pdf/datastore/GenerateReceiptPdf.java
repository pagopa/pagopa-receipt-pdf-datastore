package it.gov.pagopa.receipt.pdf.datastore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueOutput;
import com.microsoft.azure.functions.annotation.QueueTrigger;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
        Logger logger = LoggerFactory.getLogger(getClass());

        String logMsg = String.format("[%s] function called at %s for bizEvent with id %s",
                context.getFunctionName(), LocalDateTime.now(), bizEvent.getId());
        logger.info(logMsg);

        //Retrieve receipt's data from CosmosDB
        Receipt receipt;

        ReceiptCosmosClientImpl receiptCosmosClient = ReceiptCosmosClientImpl.getInstance();

        //Retrieve receipt from CosmosDB
        try {
            receipt = receiptCosmosClient.getReceiptDocument(bizEvent.getId());
        } catch (ReceiptNotFoundException e) {
            String errorMsg = String.format("[%s] Receipt not found with the biz-event id %s",
                    context.getFunctionName(), bizEvent.getId());
            throw new ReceiptNotFoundException(errorMsg, e);
        }

        int numberOfSavedPdfs = 0;

        //Verify receipt status
        if (receipt != null &&
                receipt.getEventData() != null &&
                (receipt.getStatus().equals(ReceiptStatusType.INSERTED) ||
                        receipt.getStatus().equals(ReceiptStatusType.RETRY))
        ) {

            GenerateReceiptPdfService service = new GenerateReceiptPdfService();

            //Verify if debtor's and payer's fiscal code are the same
            String debtorCF = receipt.getEventData().getDebtorFiscalCode();
            String payerCF = receipt.getEventData().getPayerFiscalCode();

            if (debtorCF != null || payerCF != null) {
                boolean generateOnlyDebtor = payerCF == null || payerCF.equals(debtorCF);

                String log = String.format(
                        "[%s] Generating pdf for Receipt with id %s",
                        context.getFunctionName(),
                        receipt.getEventId()
                );
                logger.info(log);

                //Generate and save PDF
                PdfGeneration pdfGeneration = service.handlePdfsGeneration(generateOnlyDebtor, receipt, bizEvent, debtorCF, payerCF, logger);


                log = String.format(
                        "[%s] Saving pdf for Receipt with id %s to the blob storage",
                        context.getFunctionName(),
                        receipt.getEventId()
                );
                logger.info(log);

                //Write PDF blob storage metadata on receipt
                numberOfSavedPdfs = service.addPdfsMetadataToReceipt(receipt, pdfGeneration);

                //Verify PDF generation success
                service.verifyPdfGeneration(bizEventMessage, requeueMessage, logger, receipt, generateOnlyDebtor, pdfGeneration);


            } else {
                String errorMessage = String.format(
                        "[%s] Error processing receipt with id %s : both debtor's and payer's fiscal code are null",
                        context.getFunctionName(),
                        receipt.getEventId()
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

        } else if (receipt != null) {
            String errorMessage = String.format(
                    "[%s] Receipt with id %s not in INSERTED or RETRY",
                    context.getFunctionName(),
                    receipt.getEventId()
            );
            logger.info(errorMessage);
        }



        if (!itemsToNotify.isEmpty()) {
            String log = String.format(
                    "[%s] Receipt with id %s being saved with status %s and with %s pdfs",
                    context.getFunctionName(),
                    receipt.getEventId(),
                    receipt.getStatus(),
                    numberOfSavedPdfs
            );
            logger.info(log);
            documentdb.setValue(itemsToNotify);
        }
    }
}
