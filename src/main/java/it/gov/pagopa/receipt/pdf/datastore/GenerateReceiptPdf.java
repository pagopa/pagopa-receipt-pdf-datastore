package it.gov.pagopa.receipt.pdf.datastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.PdfEngineClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptBlobClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.datastore.model.PdfMetadata;
import it.gov.pagopa.receipt.pdf.datastore.model.request.PdfEngineRequest;
import it.gov.pagopa.receipt.pdf.datastore.model.response.BlobStorageResponse;
import it.gov.pagopa.receipt.pdf.datastore.model.response.PdfEngineResponse;
import it.gov.pagopa.receipt.pdf.datastore.utils.ObjectMapperUtils;
import it.gov.pagopa.receipt.pdf.datastore.utils.ReceiptPdfUtils;
import org.apache.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.*;
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
     *      - Complete template for the payer
     *      - Partial template for the debtor
     * If the fiscal code is the same it will generate only one pdf with the complete template
     * For every pdf to generate:
     *      - call the API to the PDF Engine to generate the file from the template
     *      - the pdf is saved to the designed Azure Blob Storage
     *      - the pdf metadata retrieved from the storage are saved on the receipt's data (file name & url)
     * If everything succeeded the receipt's status will be updated to GENERATED and saved to CosmosDB
     * #
     * The message is re-sent to the queue in case of errors like:
     *      the receipt is not found;
     *      the receipt has NOT status INSERTED or RETRY;
     *      there is an error generating at least one pdf;
     *      there is an error saving at least one pdf to blob storage;
     *      errors processing the data;
     * #
     * After too many retry the receipt's status will be updated to FAILED
     *
     * @param message -> message, with biz-event's data, triggering the function
     * @param documentdb -> output binding that will update the receipt data with the pdfs metadata
     * @param requeueMessage -> output binding that will re-send the message to the queue in case of errors
     * @param context -> function context
     */
    @FunctionName("GenerateReceiptProcess")
    @ExponentialBackoffRetry(maxRetryCount = 5, minimumInterval = "500", maximumInterval = "5000")
    public void processGenerateReceipt(
            @QueueTrigger(
                    name = "QueueReceiptWaitingForGen",
                    queueName = "pagopa-d-weu-receipts-queue-receipt-waiting-4-gen",
                    connection = "RECEIPT_QUEUE_CONN_STRING")
            String message,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    collectionName = "receipts",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<Receipt>> documentdb,
            @QueueOutput(
                    name = "QueueReceiptWaitingForGenOutput",
                    queueName = "pagopa-d-weu-receipts-queue-receipt-waiting-4-gen",
                    connection = "RECEIPT_QUEUE_CONN_STRING")
            OutputBinding<String> requeueMessage,
            final ExecutionContext context) {

        //Map queue message to BizEvent
        BizEvent bizEvent = ObjectMapperUtils.mapString(message, BizEvent.class);

        if (bizEvent != null) {
            List<Receipt> itemsToNotify = new ArrayList<>();
            Logger logger = context.getLogger();

            String logMsg = String.format("GenerateReceipt function called at %s", LocalDateTime.now());
            logger.info(logMsg);

            //Retrieve receipt's data from CosmosDB
            Receipt receipt = null;

            ReceiptCosmosClientImpl receiptCosmosClient = ReceiptCosmosClientImpl.getInstance();

            //Retrieve receipt from CosmosDB
            try {
                receipt = receiptCosmosClient.getReceiptDocument(bizEvent.getId());
            } catch (ReceiptNotFoundException e) {
                requeueMessage.setValue(message);
            }

            int discarder = 0;
            int numberOfSavedPdfs = 0;

            //Verify receipt status
            if (receipt != null && (receipt.getStatus().equals(ReceiptStatusType.INSERTED) || receipt.getStatus().equals(ReceiptStatusType.RETRY))) {

                logMsg = String.format("GenerateReceipt function called at %s for event with id %s and status %s",
                        LocalDateTime.now(), bizEvent.getId(), bizEvent.getEventStatus());
                logger.info(logMsg);

                //Verify if debtor's and payer's fiscal code are the same
                if (receipt.getEventData() != null) {
                    String debtorCF = receipt.getEventData().getDebtorFiscalCode();
                    String payerCF = receipt.getEventData().getPayerFiscalCode();

                    boolean payerDebtorEqual = payerCF.equals(debtorCF);

                    //Generate and save PDF
                    PdfGeneration pdfGeneration = handlePdfsGeneration(payerDebtorEqual, receipt, bizEvent, debtorCF, payerCF);

                    //Write PDF blob storage metadata on receipt
                    numberOfSavedPdfs = ReceiptPdfUtils.addPdfsMetadataToReceipt(receipt, pdfGeneration);

                    //Verify PDF generation success
                    ReceiptPdfUtils.verifyPdfGeneration(message, requeueMessage, logger, receipt, payerDebtorEqual, pdfGeneration);

                    //Add receipt to items to be saved to CosmosDB
                    itemsToNotify.add(receipt);
                } else {
                    requeueMessage.setValue(message);
                }
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

    /**
     * Handles conditionally the generation of the PDFs based on the payerDebtorEqual boolean
     *
     * @param payerDebtorEqual -> boolean that verify if the payer and debtor have the same fiscal code
     * @param receipt -> receipt from CosmosDB
     * @param bizEvent -> biz-event from queue message
     * @param debtorCF -> debtor fiscal code
     * @param payerCF -> payer fiscal code
     * @return PdfGeneration object with the PDF metadata from the Blob Storage or relatives error messages
     */
    private PdfGeneration handlePdfsGeneration(boolean payerDebtorEqual, Receipt receipt, BizEvent bizEvent, String debtorCF, String payerCF) {
        PdfGeneration pdfGeneration = new PdfGeneration();

        if (payerDebtorEqual) {
            //Generate debtor's complete PDF
            if (receipt.getMdAttach() == null || receipt.getMdAttach().getUrl() == null || receipt.getMdAttach().getUrl().isEmpty()) {
                pdfGeneration.setDebtorMetadata(generatePdf(bizEvent, debtorCF, true));
            }
        } else {
            //Generate debtor's partial PDF
            if (receipt.getMdAttach() == null || receipt.getMdAttach().getUrl() == null || receipt.getMdAttach().getUrl().isEmpty()) {
                pdfGeneration.setDebtorMetadata(generatePdf(bizEvent, debtorCF, false));
            }
            //Generate payer's complete PDF
            if (receipt.getMdAttachPayer() == null || receipt.getMdAttachPayer().getUrl() == null || receipt.getMdAttachPayer().getUrl().isEmpty()) {
                pdfGeneration.setPayerMetadata(generatePdf(bizEvent, payerCF, true));
            }
        }

        return pdfGeneration;
    }

    /**
     * Handles PDF generation and saving to storage
     *
     * @param bizEvent -> biz-event from queue message
     * @param fiscalCode -> debtor or payer fiscal code
     * @param completeTemplate -> boolean that indicates what template to use
     * @return PDF metadata retrieved from Blob Storage or relative error message
     */
    private PdfMetadata generatePdf(BizEvent bizEvent, String fiscalCode, boolean completeTemplate) {
        PdfEngineRequest request = new PdfEngineRequest();
        PdfMetadata response = new PdfMetadata();

        //Get filename
        String completeTemplateFileName = System.getenv().getOrDefault("COMPLETE_TEMPLATE_FILE_NAME", "complete_template.zip");
        String partialTemplateFileName = System.getenv().getOrDefault("PARTIAL_TEMPLATE_FILE_NAME", "partial_template.zip");

        String fileName = completeTemplate ? completeTemplateFileName : partialTemplateFileName;

        try {
            //File to byte[]
            byte[] htmlTemplate = GenerateReceiptPdf.class.getClassLoader().getResourceAsStream(fileName).readAllBytes();

            //Build the request
            request.setTemplate(htmlTemplate);
            request.setData(ObjectMapperUtils.writeValueAsString(ReceiptPdfUtils.convertReceiptToPdfData(bizEvent)));

            request.setApplySignature(false);

            PdfEngineClientImpl pdfEngineClient = PdfEngineClientImpl.getInstance();

            //Call the PDF Engine
            PdfEngineResponse pdfEngineResponse = pdfEngineClient.generatePDF(request);

            if (pdfEngineResponse.getStatusCode() == HttpStatus.SC_OK) {
                //Save the PDF
                String pdfFileName = bizEvent.getId() + fiscalCode;

                handleSaveToBlobStorage(response, pdfEngineResponse, pdfFileName);

            } else {
                //Handle PDF generation error
                response.setStatusCode(ReasonErrorCode.ERROR_PDF_ENGINE.getCustomCode(pdfEngineResponse.getStatusCode()));
                response.setErrorMessage(pdfEngineResponse.getErrorMessage());
            }

            return response;

        } catch (Exception e) {
            //Handle file not found error
            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            response.setErrorMessage("File template not found, error: " + e);
            return response;
        }
    }

    /**
     * Handles saving PDF to Blob Storage
     *
     * @param response -> pdf metadata containing response
     * @param pdfEngineResponse -> response from the pdf engine
     * @param pdfFileName -> filename composed of biz-event id and user fiscal code
     */
    private static void handleSaveToBlobStorage(PdfMetadata response, PdfEngineResponse pdfEngineResponse, String pdfFileName) {
        BlobStorageResponse blobStorageResponse;

        ReceiptBlobClientImpl blobClient = ReceiptBlobClientImpl.getInstance();

        //Save to Blob Storage
        try {
            blobStorageResponse = blobClient.savePdfToBlobStorage(pdfEngineResponse.getPdf(), pdfFileName);

            if (blobStorageResponse.getStatusCode() == com.microsoft.azure.functions.HttpStatus.CREATED.value()) {
                //Update PDF metadata
                response.setDocumentName(blobStorageResponse.getDocumentName());
                response.setDocumentUrl(blobStorageResponse.getDocumentUrl());

                response.setStatusCode(HttpStatus.SC_OK);

            } else {
                //Handle Blob storage error
                response.setStatusCode(ReasonErrorCode.ERROR_BLOB_STORAGE.getCode());
                response.setErrorMessage("Error saving pdf to blob storage");
            }

        } catch (Exception e) {
            //Handle Blob storage error
            response.setStatusCode(ReasonErrorCode.ERROR_BLOB_STORAGE.getCode());
            response.setErrorMessage("Error saving pdf to blob storage : " + e);
        }
    }
}
