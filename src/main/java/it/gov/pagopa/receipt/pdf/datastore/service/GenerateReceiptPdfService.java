package it.gov.pagopa.receipt.pdf.datastore.service;

import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.PdfEngineClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptBlobClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReceiptMetadata;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.datastore.model.PdfMetadata;
import it.gov.pagopa.receipt.pdf.datastore.model.request.PdfEngineRequest;
import it.gov.pagopa.receipt.pdf.datastore.model.response.BlobStorageResponse;
import it.gov.pagopa.receipt.pdf.datastore.model.response.PdfEngineResponse;
import it.gov.pagopa.receipt.pdf.datastore.utils.ObjectMapperUtils;
import it.gov.pagopa.receipt.pdf.datastore.utils.TemplateMapperUtils;
import lombok.NoArgsConstructor;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.time.LocalDateTime;

@NoArgsConstructor
public class GenerateReceiptPdfService {

    private static final int MAX_NUMBER_RETRY = Integer.parseInt(System.getenv().getOrDefault("COSMOS_RECEIPT_QUEUE_MAX_RETRY", "5"));

    /**
     * Handles conditionally the generation of the PDFs based on the generateOnlyDebtor boolean
     *
     * @param generateOnlyDebtor Boolean that verify if the payer and debtor have the same fiscal code
     * @param receipt            Receipt from CosmosDB
     * @param bizEvent           Biz-event from queue message
     * @param debtorCF           Debtor fiscal code
     * @param payerCF            Payer fiscal code
     * @return PdfGeneration object with the PDF metadata from the Blob Storage or relatives error messages
     */
    public PdfGeneration handlePdfsGeneration(boolean generateOnlyDebtor, Receipt receipt, BizEvent bizEvent, String debtorCF, String payerCF, Logger logger) {
        PdfGeneration pdfGeneration = new PdfGeneration();

        if (generateOnlyDebtor) {
            //Generate debtor's complete PDF
            if (debtorCF != null &&
                    (receipt.getMdAttach() == null ||
                            receipt.getMdAttach().getUrl() == null ||
                            receipt.getMdAttach().getUrl().isEmpty())
            ) {
                pdfGeneration.setDebtorMetadata(generatePdf(bizEvent, debtorCF, true, logger));
            }
        } else {
            //Generate debtor's partial PDF
            if (debtorCF != null &&
                    (receipt.getMdAttach() == null ||
                            receipt.getMdAttach().getUrl() == null ||
                            receipt.getMdAttach().getUrl().isEmpty())
            ) {
                pdfGeneration.setDebtorMetadata(generatePdf(bizEvent, debtorCF, false, logger));
            }
            //Generate payer's complete PDF
            if (payerCF != null &&
                    (receipt.getMdAttachPayer() == null ||
                            receipt.getMdAttachPayer().getUrl() == null ||
                            receipt.getMdAttachPayer().getUrl().isEmpty())
            ) {
                pdfGeneration.setPayerMetadata(generatePdf(bizEvent, payerCF, true, logger));
            }
        }

        return pdfGeneration;
    }

    /**
     * Handles PDF generation and saving to storage
     *
     * @param bizEvent         Biz-event from queue message
     * @param fiscalCode       Debtor or payer fiscal code
     * @param completeTemplate Boolean that indicates what template to use
     * @return PDF metadata retrieved from Blob Storage or relative error message
     */
    private PdfMetadata generatePdf(BizEvent bizEvent, String fiscalCode, boolean completeTemplate, Logger logger) {
        PdfEngineRequest request = new PdfEngineRequest();
        PdfMetadata response = new PdfMetadata();

        //Get filename
        String completeTemplateFileName = System.getenv().getOrDefault("COMPLETE_TEMPLATE_FILE_NAME", "complete_template.zip");
        String partialTemplateFileName = System.getenv().getOrDefault("PARTIAL_TEMPLATE_FILE_NAME", "partial_template.zip");

        String fileName = completeTemplate ? completeTemplateFileName : partialTemplateFileName;

        try {
            URL templateStream = GenerateReceiptPdfService.class.getClassLoader().getResource(fileName);
            //Build the request
            request.setTemplate(templateStream);
            request.setData(ObjectMapperUtils.writeValueAsString(TemplateMapperUtils.convertReceiptToPdfData(bizEvent)));

            request.setApplySignature(false);

            PdfEngineClientImpl pdfEngineClient = PdfEngineClientImpl.getInstance();

            //Call the PDF Engine
            PdfEngineResponse pdfEngineResponse = pdfEngineClient.generatePDF(request);

            if (pdfEngineResponse.getStatusCode() == HttpStatus.SC_OK) {
                //Save the PDF
                String pdfFileName = bizEvent.getId() + fiscalCode;

                handleSaveToBlobStorage(pdfEngineResponse, response, pdfFileName, logger);

            } else {
                //Handle PDF generation error
                response.setStatusCode(ReasonErrorCode.ERROR_PDF_ENGINE.getCustomCode(pdfEngineResponse.getStatusCode()));
                response.setErrorMessage(pdfEngineResponse.getErrorMessage());
            }

        } catch (Exception e) {
            //Handle file not found error
            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            response.setErrorMessage("File template not found, error: " + e);
        }

        return response;
    }

    /**
     * Handles saving PDF to Blob Storage
     *
     * @param response    Pdf metadata containing response
     * @param pdfFileName Filename composed of biz-event id and user fiscal code
     */
    private void handleSaveToBlobStorage(PdfEngineResponse pdfEngineResponse, PdfMetadata response, String pdfFileName, Logger logger) {
        BlobStorageResponse blobStorageResponse;

        ReceiptBlobClientImpl blobClient = ReceiptBlobClientImpl.getInstance();

        String tempPdfPath = pdfEngineResponse.getTempPdfPath();
        String tempDirectoryPath = pdfEngineResponse.getTempDirectoryPath();

        //Save to Blob Storage
        try (BufferedInputStream pdfStream = new BufferedInputStream(new FileInputStream(tempPdfPath))) {
            blobStorageResponse = blobClient.savePdfToBlobStorage(pdfStream, pdfFileName);

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

        deleteTempFolderAndFile(tempPdfPath, tempDirectoryPath, logger);
    }

    /**
     * Delete temporary file and director created for the generated PDF
     *
     * @param tempPdfPath       Path to the temp PDF file
     * @param tempDirectoryPath Path to the temp directory containing the PDF file
     * @param logger            Logger
     */
    private static void deleteTempFolderAndFile(String tempPdfPath, String tempDirectoryPath, Logger logger) {
        String logMsg;

        File tempFile = new File(tempPdfPath);
        if (tempFile.exists()) {
            try {
                Files.delete(tempFile.toPath());
            } catch (IOException e) {
                logMsg = String.format("Error deleting temporary pdf file from file system: %s", e);
                logger.warn(logMsg);
            }
        }

        File tempDirectory = new File(tempDirectoryPath);
        if (tempDirectory.exists()) {
            try {
                Files.delete(tempDirectory.toPath());
            } catch (IOException e) {
                logMsg = String.format("Error deleting temporary pdf directory from file system: %s", e);
                logger.warn(logMsg);
            }
        }
    }


    /**
     * Adds PDF metadata from the Blob Storage to the receipt to be saved on CosmosDB
     *
     * @param receipt     Receipt to be saved
     * @param responseGen Response from the PDF generation process
     * @return number of pdfs saved to blob storage
     */
    public int addPdfsMetadataToReceipt(Receipt receipt, PdfGeneration responseGen) {
        PdfMetadata debtorMetadata = responseGen.getDebtorMetadata();
        PdfMetadata payerMetadata = responseGen.getPayerMetadata();

        int numberOfSavedPdfs = 0;

        if (debtorMetadata != null && debtorMetadata.getStatusCode() == HttpStatus.SC_OK) {
            ReceiptMetadata receiptMetadata = new ReceiptMetadata();
            receiptMetadata.setName(debtorMetadata.getDocumentName());
            receiptMetadata.setUrl(debtorMetadata.getDocumentUrl());

            receipt.setMdAttach(receiptMetadata);
            numberOfSavedPdfs++;
        }

        if (payerMetadata != null && payerMetadata.getStatusCode() == HttpStatus.SC_OK) {
            ReceiptMetadata receiptMetadata = new ReceiptMetadata();
            receiptMetadata.setName(payerMetadata.getDocumentName());
            receiptMetadata.setUrl(payerMetadata.getDocumentUrl());

            receipt.setMdAttachPayer(receiptMetadata);
            numberOfSavedPdfs++;
        }

        return numberOfSavedPdfs;
    }

    /**
     * Verifies if the PDF generation process succeeded
     * In case of errors updates the receipt status and error message and re-sends the queue message to the queue
     *
     * @param bizEventMessage    Queue message
     * @param requeueMessage     Output binding for sending messages to the queue
     * @param logger             Function logger
     * @param receipt            Receipt to be updated
     * @param generateOnlyDebtor Boolean to verify if debtor's and payer's fiscal code is the same
     * @param pdfGeneration      Response from the PDF generation process
     */
    public void verifyPdfGeneration(String bizEventMessage, OutputBinding<String> requeueMessage, Logger logger, Receipt receipt, boolean generateOnlyDebtor, PdfGeneration pdfGeneration) {
        PdfMetadata responseDebtorGen = pdfGeneration.getDebtorMetadata();
        PdfMetadata responsePayerGen = pdfGeneration.getPayerMetadata();

        //Verify if all the needed PDFs have been generated
        if (responseDebtorGen != null &&
                responseDebtorGen.getStatusCode() == HttpStatus.SC_OK &&
                (generateOnlyDebtor ||
                        (responsePayerGen != null &&
                                responsePayerGen.getStatusCode() == HttpStatus.SC_OK))
        ) {
            receipt.setStatus(ReceiptStatusType.GENERATED);
        } else {
            ReceiptStatusType receiptStatusType;
            //Verify if the max number of retry have been passed
            if (receipt.getNumRetry() > MAX_NUMBER_RETRY) {
                receiptStatusType = ReceiptStatusType.FAILED;
            } else {
                receiptStatusType = ReceiptStatusType.RETRY;
            }

            PdfMetadata failedResponse = returnFailedResponse(responseDebtorGen, responsePayerGen);

            int errorStatusCode = failedResponse != null ? failedResponse.getStatusCode() : HttpStatus.SC_INTERNAL_SERVER_ERROR;
            String errorMessage = failedResponse != null ? failedResponse.getErrorMessage() : "Unknown error";

            //Update the receipt's status and error message
            handleErrorGeneratingReceipt(
                    receiptStatusType,
                    errorStatusCode,
                    errorMessage,
                    bizEventMessage,
                    receipt,
                    requeueMessage,
                    logger
            );
        }
    }

    /**
     * Return the failed response between the debtor's and the payer's one
     *
     * @param responseDebtorGen Debtor pdf generation response
     * @param responsePayerGen  Payer pdf generation response
     * @return the failed response
     */
    private static PdfMetadata returnFailedResponse(PdfMetadata responseDebtorGen, PdfMetadata responsePayerGen) {
        PdfMetadata failedResponse = null;

        if (responseDebtorGen != null && responseDebtorGen.getStatusCode() != HttpStatus.SC_OK) {
            failedResponse = responseDebtorGen;

        }
        if (responsePayerGen != null && responsePayerGen.getStatusCode() != HttpStatus.SC_OK) {
            failedResponse = responsePayerGen;
        }
        return failedResponse;
    }

    /**
     * Handles errors updating receipt status and error message,
     * re-sends the biz-event message to the queue,
     * logs error message
     *
     * @param receiptStatusType Status to update the receipt
     * @param errorStatusCode   Error code
     * @param errorMessage      Error message
     * @param bizEventMessage   BizEvent message from queue
     * @param receipt           Receipt to be saved on CosmosDB
     * @param requeueMessage    Output Binding to save the bizEventMessage
     * @param logger            Logger
     */
    public void handleErrorGeneratingReceipt(
            ReceiptStatusType receiptStatusType,
            int errorStatusCode,
            String errorMessage,
            String bizEventMessage,
            Receipt receipt,
            OutputBinding<String> requeueMessage,
            Logger logger) {

        receipt.setStatus(receiptStatusType);
        receipt.setNumRetry(receipt.getNumRetry() + 1);
        //Update the receipt's status and error message
        ReasonError reasonError = new ReasonError(errorStatusCode, errorMessage);
        receipt.setReasonErr(reasonError);

        //Re-queue the message
        requeueMessage.setValue(bizEventMessage);
        String logMessage = "Error generating PDF at " + LocalDateTime.now() + " : " + errorMessage;
        logger.error(logMessage);
    }
}
