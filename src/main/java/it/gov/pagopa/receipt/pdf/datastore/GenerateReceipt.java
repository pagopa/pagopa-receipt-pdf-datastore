package it.gov.pagopa.receipt.pdf.datastore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.receipt.pdf.datastore.client.PdfEngineClient;
import it.gov.pagopa.receipt.pdf.datastore.client.PdfEngineClientProvider;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.PdfEngineClientProviderImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.model.request.PdfEngineRequest;
import it.gov.pagopa.receipt.pdf.datastore.model.response.PdfEngineResponse;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.utils.ObjectMapperUtils;
import org.apache.commons.io.IOUtils;

import javax.swing.text.html.HTML;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public class GenerateReceipt {

    private ReceiptCosmosClientImpl receiptCosmosClient;

    private PdfEngineClient pdfEngineClient;

    GenerateReceipt(ReceiptCosmosClientImpl receiptCosmosClient, PdfEngineClient pdfEngineClient) {
        this.receiptCosmosClient = receiptCosmosClient;
        this.pdfEngineClient = pdfEngineClient;
    }

    GenerateReceipt() {
        this.receiptCosmosClient = new ReceiptCosmosClientImpl();
        this.pdfEngineClient = new PdfEngineClientProviderImpl().provideClient();
    }

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

        //Retrieve receipt's data from CosmosDB
        Receipt receipt = null;
        try {
            receipt = receiptCosmosClient.getReceiptDocument(bizEvent.getId());
        } catch (ReceiptNotFoundException e) {
            requeueMessage.setValue(messageText);
        }

        if (receipt != null && receipt.getStatus().equals(ReceiptStatusType.INSERTED)) {

            logMsg = String.format("GenerateReceipt function called at %s for event with id %s and status %s",
                    LocalDateTime.now(), bizEvent.getId(), bizEvent.getEventStatus());
            logger.info(logMsg);

            //Verify debtor's fiscal code is different from the payer's one
            String debtorCF = receipt.getEventData().getDebtorFiscalCode();
            String payerCF = receipt.getEventData().getPayerFiscalCode();

            boolean payerDebtorEqual = payerCF.equals(debtorCF);

            PdfEngineResponse statusDebtorGen = new PdfEngineResponse();
            PdfEngineResponse statusPayerGen = new PdfEngineResponse();

            //Generate PDFs for the needed fiscal codes
            //TODO generate complete template
            if (payerDebtorEqual) {
                if (receipt.getMdAttach().getName().isEmpty()) {
                    statusDebtorGen = handleReceiptPDFGeneration(receipt, true);
                }
            } else {
                //TODO generate partial template
                //verify the debot pdf hasn't already been generated
                if (receipt.getMdAttach().getName().isEmpty()) {
                    statusDebtorGen = handleReceiptPDFGeneration(receipt, false);
                }
                //TODO generate complete template
                //verify the payer pdf hasn't already been generated
                if (receipt.getMdAttachPayer().getName().isEmpty()) {
                    statusPayerGen = handleReceiptPDFGeneration(receipt, true);
                }
            }

            //Verify pdf generation success
            if (statusDebtorGen.getStatusCode() == 200 && statusPayerGen.getStatusCode() == 200) {
                //TODO update receipt with PDF metadata
                receipt.setStatus(ReceiptStatusType.GENERATED);
            } else {
                //TODO menage in configuration max number of retry -> then FAILED
                if (receipt.getNumRetry() > 5) {
                    receipt.setStatus(ReceiptStatusType.FAILED);
                } else {
                    receipt.setStatus(ReceiptStatusType.RETRY);
                }
                //TODO menage api call error code -> pdfengineStatusCode + add PDFEngine error message
                int errorStatusCode = statusDebtorGen.getStatusCode() == 200 ? statusPayerGen.getStatusCode() : statusDebtorGen.getStatusCode();
                ReasonError reasonError = new ReasonError(ReasonErrorCode.ERROR_PDF_ENGINE.getCustomCode(errorStatusCode), "Error generating PDF: ");
                receipt.setReasonErr(reasonError);
                receipt.setNumRetry(receipt.getNumRetry() + 1);

                requeueMessage.setValue(messageText);
                //TODO add PDFEngine error message
                logger.severe("Error generating PDF at " + LocalDateTime.now() + " : ");
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

    private PdfEngineResponse handleReceiptPDFGeneration(Receipt receipt, boolean completeTemplate) {
        int pdfEngineStatusCode = 0;
        PdfEngineRequest request = new PdfEngineRequest();
        PdfEngineResponse response = new PdfEngineResponse();
        String templateFileName = completeTemplate ? "/complete_template.html" : "/partial_template.html";

        String htmlTemplate = null;
        try {
            htmlTemplate = IOUtils.toString(
                    Objects.requireNonNull(this.getClass().getResourceAsStream(templateFileName)),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            response.setStatusCode(400);

            return response;
        }

        request.setTemplate(htmlTemplate);

        response = pdfEngineClient.generatePDF(request);

        //TODO add call to PDFEngine service to generate pdf (full o partial template)

        response.setStatusCode(pdfEngineStatusCode);

        return response;
    }
}
