package it.gov.pagopa.receipt.pdf.datastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.PdfEngineClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptBlobClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReceiptMetadata;
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

public class GenerateReceiptPdf {

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

        BizEvent bizEvent = ObjectMapperUtils.mapString(message, BizEvent.class);

        if (bizEvent != null) {
            List<Receipt> itemsToNotify = new ArrayList<>();
            Logger logger = context.getLogger();

            String logMsg = String.format("GenerateReceipt function called at %s", LocalDateTime.now());
            logger.info(logMsg);

            //Retrieve receipt's data from CosmosDB
            Receipt receipt = null;

            ReceiptCosmosClientImpl receiptCosmosClient = ReceiptCosmosClientImpl.getInstance();

            try {
                receipt = receiptCosmosClient.getReceiptDocument(bizEvent.getId());
            } catch (ReceiptNotFoundException e) {
                requeueMessage.setValue(message);
            }

            if (receipt != null && (receipt.getStatus().equals(ReceiptStatusType.INSERTED) || receipt.getStatus().equals(ReceiptStatusType.RETRY))) {

                logMsg = String.format("GenerateReceipt function called at %s for event with id %s and status %s",
                        LocalDateTime.now(), bizEvent.getId(), bizEvent.getEventStatus());
                logger.info(logMsg);

                //Verify debtor's fiscal code is different from the payer's one
                if (receipt.getEventData() != null) {
                    String debtorCF = receipt.getEventData().getDebtorFiscalCode();
                    String payerCF = receipt.getEventData().getPayerFiscalCode();

                    boolean payerDebtorEqual = payerCF.equals(debtorCF);

                    PdfGeneration pdfGeneration = handlePdfsGeneration(payerDebtorEqual, receipt, bizEvent, debtorCF, payerCF);

                    //write pdf metadata on receipt
                    ReceiptPdfUtils.addPdfsMetadataToReceipt(receipt, pdfGeneration);

                    //Verify pdf generation success
                    ReceiptPdfUtils.verifyPdfGeneration(message, requeueMessage, logger, receipt, payerDebtorEqual, pdfGeneration);

                    itemsToNotify.add(receipt);
                }
            }

            if (!itemsToNotify.isEmpty()) {
                documentdb.setValue(itemsToNotify);
            }
        } else {
            requeueMessage.setValue(message);
        }
    }

    private PdfGeneration handlePdfsGeneration(boolean payerDebtorEqual, Receipt receipt, BizEvent bizEvent, String debtorCF, String payerCF) {
        PdfGeneration pdfGeneration = new PdfGeneration();

        if (payerDebtorEqual) {
            if (receipt.getMdAttach() == null || receipt.getMdAttach().getUrl() == null || receipt.getMdAttach().getUrl().isEmpty()) {
                pdfGeneration.setDebtorMetadata(generatePdf(bizEvent, debtorCF, true));
            }
        } else {
            //verify the debtor pdf hasn't already been generated
            if (receipt.getMdAttach() == null || receipt.getMdAttach().getUrl() == null || receipt.getMdAttach().getUrl().isEmpty()) {
                pdfGeneration.setDebtorMetadata(generatePdf(bizEvent, debtorCF, false));
            }
            //verify the payer pdf hasn't already been generated
            if (receipt.getMdAttachPayer() == null || receipt.getMdAttachPayer().getUrl() == null || receipt.getMdAttachPayer().getUrl().isEmpty()) {
                pdfGeneration.setPayerMetadata(generatePdf(bizEvent, payerCF, true));
            }
        }

        return pdfGeneration;
    }

    private PdfMetadata generatePdf(BizEvent bizEvent, String fiscalCode, boolean completeTemplate) {
        PdfEngineRequest request = new PdfEngineRequest();
        PdfMetadata response = new PdfMetadata();

        String completeTemplateFileName = System.getenv().getOrDefault("COMPLETE_TEMPLATE_FILE_NAME", "complete_template.zip");
        String partialTemplateFileName = System.getenv().getOrDefault("PARTIAL_TEMPLATE_FILE_NAME", "partial_template.zip");

        String fileName = completeTemplate ? completeTemplateFileName : partialTemplateFileName;

        try {
            byte[] htmlTemplate = GenerateReceiptPdf.class.getClassLoader().getResourceAsStream(fileName).readAllBytes();

            request.setTemplate(htmlTemplate);
            request.setData(ObjectMapperUtils.writeValueAsString(ReceiptPdfUtils.convertReceiptToPdfData(bizEvent)));

            request.setApplySignature(false);

            PdfEngineClientImpl pdfEngineClient = PdfEngineClientImpl.getInstance();

            PdfEngineResponse pdfEngineResponse = pdfEngineClient.generatePDF(request);

            if (pdfEngineResponse.getStatusCode() == HttpStatus.SC_OK) {
                ReceiptBlobClientImpl blobClient = ReceiptBlobClientImpl.getInstance();
                String pdfFileName = bizEvent.getId() + fiscalCode;

                handleSaveToBlobStorage(response, pdfEngineResponse, blobClient, pdfFileName);

            } else {
                response.setStatusCode(ReasonErrorCode.ERROR_PDF_ENGINE.getCustomCode(pdfEngineResponse.getStatusCode()));
                response.setErrorMessage(pdfEngineResponse.getErrorMessage());
            }

            return response;

        } catch (Exception e) {
            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            response.setErrorMessage("File template not found, error: " + e);
            return response;
        }
    }

    private static void handleSaveToBlobStorage(PdfMetadata response, PdfEngineResponse pdfEngineResponse, ReceiptBlobClientImpl blobClient, String pdfFileName) {
        BlobStorageResponse blobStorageResponse;

        try {
            blobStorageResponse = blobClient.savePdfToBlobStorage(pdfEngineResponse.getPdf(), pdfFileName);

            if (blobStorageResponse.getStatusCode() == com.microsoft.azure.functions.HttpStatus.CREATED.value()) {
                response.setDocumentName(blobStorageResponse.getDocumentName());
                response.setDocumentUrl(blobStorageResponse.getDocumentUrl());

                response.setStatusCode(HttpStatus.SC_OK);
            } else {
                response.setStatusCode(ReasonErrorCode.ERROR_BLOB_STORAGE.getCode());
                response.setErrorMessage("Error saving pdf to blob storage");
            }

        } catch (Exception e) {
            response.setStatusCode(ReasonErrorCode.ERROR_BLOB_STORAGE.getCode());
            response.setErrorMessage("Error saving pdf to blob storage : " + e);
        }
    }
}
