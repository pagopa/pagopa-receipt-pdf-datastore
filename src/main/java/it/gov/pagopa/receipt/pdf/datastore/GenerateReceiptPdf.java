package it.gov.pagopa.receipt.pdf.datastore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.PdfEngineClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptBlobClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.Info;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.Transaction;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.TransactionDetails;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReceiptMetadata;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.datastore.model.request.PdfEngineRequest;
import it.gov.pagopa.receipt.pdf.datastore.model.response.BlobStorageResponse;
import it.gov.pagopa.receipt.pdf.datastore.model.response.PdfEngineResponse;
import it.gov.pagopa.receipt.pdf.datastore.utils.ObjectMapperUtils;
import org.apache.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class GenerateReceiptPdf {

    public final int maxNumberRetry = Integer.parseInt(System.getenv().getOrDefault("COSMOS_RECEIPT_QUEUE_MAX_RETRY", "5"));

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
        BizEvent bizEvent = new BizEvent();

        try {
            bizEvent = ObjectMapperUtils.mapString(message, BizEvent.class);
        } catch (JsonProcessingException e) {
            requeueMessage.setValue(message);
        }

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

                PdfGeneration responseDebtorGen = new PdfGeneration();
                PdfGeneration responsePayerGen = new PdfGeneration();

                //Generate PDFs for the needed fiscal codes
                if (payerDebtorEqual) {
                    if (receipt.getMdAttach() == null || receipt.getMdAttach().getUrl() == null ||  receipt.getMdAttach().getUrl().isEmpty()) {
                        responseDebtorGen = handleReceiptPDFGeneration(bizEvent, debtorCF, true);
                    }
                } else {
                    //verify the debtor pdf hasn't already been generated
                    if (receipt.getMdAttach() == null || receipt.getMdAttach().getUrl() == null ||  receipt.getMdAttach().getUrl().isEmpty()) {
                        responseDebtorGen = handleReceiptPDFGeneration(bizEvent, debtorCF, false);
                    }
                    //verify the payer pdf hasn't already been generated
                    if (receipt.getMdAttachPayer() == null || receipt.getMdAttachPayer().getUrl() == null || receipt.getMdAttachPayer().getUrl().isEmpty()) {
                        responsePayerGen = handleReceiptPDFGeneration(bizEvent, payerCF, true);
                    }
                }

                //write pdf metadata on receipt
                if(responseDebtorGen.getStatusCode() == HttpStatus.SC_OK) {
                    ReceiptMetadata receiptMetadata = new ReceiptMetadata();
                    receiptMetadata.setName(responseDebtorGen.getDocumentName());
                    receiptMetadata.setUrl(responseDebtorGen.getDocumentUrl());

                    receipt.setMdAttach(receiptMetadata);
                }

                if(responsePayerGen.getStatusCode() == HttpStatus.SC_OK) {
                    ReceiptMetadata receiptMetadata = new ReceiptMetadata();
                    receiptMetadata.setName(responsePayerGen.getDocumentName());
                    receiptMetadata.setUrl(responsePayerGen.getDocumentUrl());

                    receipt.setMdAttachPayer(receiptMetadata);
                }

                //Verify pdf generation success
                if (responseDebtorGen.getStatusCode() == HttpStatus.SC_OK && (payerDebtorEqual || responsePayerGen.getStatusCode() == HttpStatus.SC_OK)) {
                    receipt.setStatus(ReceiptStatusType.GENERATED);
                } else {
                    if (receipt.getNumRetry() > maxNumberRetry) {
                        receipt.setStatus(ReceiptStatusType.FAILED);
                    } else {
                        receipt.setStatus(ReceiptStatusType.RETRY);
                    }

                    int errorStatusCode = responseDebtorGen.getStatusCode() == HttpStatus.SC_OK ? responsePayerGen.getStatusCode() : responseDebtorGen.getStatusCode();
                    String errorMessage = responseDebtorGen.getErrorMessage() == null ? responsePayerGen.getErrorMessage() : responseDebtorGen.getErrorMessage();
                    ReasonError reasonError = new ReasonError(errorStatusCode, errorMessage);
                    receipt.setReasonErr(reasonError);
                    receipt.setNumRetry(receipt.getNumRetry() + 1);

                    requeueMessage.setValue(message);
                    logger.severe("Error generating PDF at " + LocalDateTime.now() + " : " + errorMessage);
                }

                itemsToNotify.add(receipt);
            }
        }

        if (itemsToNotify.size() > 0) {
            documentdb.setValue(itemsToNotify);
        }
    }

    private PdfGeneration handleReceiptPDFGeneration(BizEvent bizEvent, String fiscalCode, boolean completeTemplate) {
        PdfEngineRequest request = new PdfEngineRequest();
        PdfGeneration response = new PdfGeneration();

        String fileName = completeTemplate ? "complete_template.zip" : "partial_template.zip";

        try {
            byte[] htmlTemplate = GenerateReceiptPdf.class.getClassLoader().getResourceAsStream(fileName).readAllBytes();

            request.setTemplate(htmlTemplate);
            request.setData(ObjectMapperUtils.writeValueAsString(convertReceiptToPdfData(bizEvent)));

            request.setApplySignature(false);

            PdfEngineClientImpl pdfEngineClient = PdfEngineClientImpl.getInstance();

            PdfEngineResponse pdfEngineResponse = pdfEngineClient.generatePDF(request);

            if (pdfEngineResponse.getStatusCode() == HttpStatus.SC_OK) {
                ReceiptBlobClientImpl blobClient = ReceiptBlobClientImpl.getInstance();
                String pdfFileName = bizEvent.getId() + fiscalCode;

                BlobStorageResponse blobStorageResponse = blobClient.savePdfToBlobStorage(pdfEngineResponse.getPdf(), pdfFileName);

                if(blobStorageResponse.getStatusCode() == com.microsoft.azure.functions.HttpStatus.CREATED.value()){
                    response.setDocumentName(blobStorageResponse.getDocumentName());
                    response.setDocumentUrl(blobStorageResponse.getDocumentUrl());

                    response.setStatusCode(HttpStatus.SC_OK);
                } else {
                    response.setStatusCode(ReasonErrorCode.ERROR_BLOB_STORAGE.getCode());
                    response.setErrorMessage("Error saving pdf to blob storage");
                }

            } else {
                response.setStatusCode(ReasonErrorCode.ERROR_PDF_ENGINE.getCustomCode(pdfEngineResponse.getStatusCode()));
                response.setErrorMessage(pdfEngineResponse.getErrorMessage());
            }

            return response;

        } catch (Exception e) {
            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            response.setErrorMessage("Generic error in pdf generation:" + e);
            return response;
        }
    }

    private Map<String, Object> convertReceiptToPdfData(BizEvent bizEvent) {
        Map<String, Object> map = new HashMap<String, Object>();

        TransactionDetails transactionDetails = bizEvent.getTransactionDetails();
        Transaction transaction = transactionDetails != null ? transactionDetails.getTransaction() : null;
        Info transactionInfo = transactionDetails != null && transactionDetails.getWallet() != null
                ? transactionDetails.getWallet().getInfo() : null;

        //transaction
        Map<String, Object> transactionMap = new HashMap<String, Object>();
        //transaction.psp
        Map<String, Object> transactionPspMap = new HashMap<String, Object>();
        transactionPspMap.put(
                "name",
                transaction != null && transaction.getPsp() != null ? transaction.getPsp().getBusinessName() : ""
        );
        //transaction.psp.fee
        Map<String, Object> transactionPspFeeMap = new HashMap<String, Object>();
        transactionPspFeeMap.put(
                "amount",
                transaction != null ? transaction.getFee() : ""
        );
        transactionPspMap.put(
                "fee",
                transactionPspFeeMap
        );
        //transaction.paymentMethod
        Map<String, String> transactionPaymentMethod = new HashMap<String, String>();
        transactionPaymentMethod.put(
                "name", bizEvent.getPaymentInfo() != null ? bizEvent.getPaymentInfo().getPaymentMethod() : ""
        );
        transactionPaymentMethod.put(
                "logo", transactionInfo != null ? transactionInfo.getBrand() : ""
        );
        transactionPaymentMethod.put(
                "accountHolder",
                transactionInfo != null ? transactionInfo.getHolder() : ""
        );
        transactionPaymentMethod.put(
                "extraFee",
                "false"
        );

        transactionMap.put(
                "id",
                transaction != null ? transaction.getIdTransaction() : ""
        );
        transactionMap.put(
                "timestamp",
                bizEvent.getPaymentInfo() != null ? bizEvent.getPaymentInfo().getPaymentDateTime() : ""
        );
        transactionMap.put(
                "amount",
                bizEvent.getPaymentInfo() != null ? bizEvent.getPaymentInfo().getAmount() : ""
        );
        transactionMap.put(
                "psp",
                transactionPspMap);
        transactionMap.put(
                "rrn",
                transaction != null ? transaction.getRrn() : ""
        );
        transactionMap.put(
                "paymentMethod",
                transactionPaymentMethod
        );
        transactionMap.put(
                "authCode",
                transaction != null ? transaction.getAuthorizationCode() : ""
        );

        //user
        Map<String, Object> userMap = new HashMap<String, Object>();
        //user.data
        Map<String, Object> userDataMap = new HashMap<String, Object>();
        userDataMap.put(
                "firstName",
                transactionDetails != null && transactionDetails.getUser() != null ? transactionDetails.getUser().getFullName() : ""
        );
        userDataMap.put(
                "lastName",
                ""
        );
        userDataMap.put(
                "taxCode",
                transactionDetails != null && transactionDetails.getUser() != null ? transactionDetails.getUser().getFiscalCode() : ""
        );

        userMap.put(
                "data",
                userDataMap
        );
        userMap.put(
                "mail",
                bizEvent.getDebtor() != null ? bizEvent.getDebtor().getEMail() : ""
        );

        //cart
        Map<String, Object> cartMap = new HashMap<String, Object>();
        //cart.items
        ArrayList<Object> cartItemsArray = new ArrayList<>();
        //cart.items[0]
        Map<String, Object> cartItemMap = new HashMap<String, Object>();
        //cart.items[0].refNumber
        Map<String, Object> cartItemRefNumberMap = new HashMap<String, Object>();
        cartItemRefNumberMap.put(
                "type",
                "CODICE AVVISO"
        );
        cartItemRefNumberMap.put(
                "value",
                bizEvent.getDebtorPosition() != null ? bizEvent.getDebtorPosition().getIuv() : "")
        ;
        //cart.items[0].debtor
        Map<String, Object> cartItemDebtorMap = new HashMap<String, Object>();
        cartItemDebtorMap.put(
                "fullName",
                bizEvent.getDebtor() != null ? bizEvent.getDebtor().getFullName() : ""
        );
        cartItemDebtorMap.put(
                "taxCode",
                bizEvent.getDebtor() != null ? bizEvent.getDebtor().getEntityUniqueIdentifierType() : ""
        );
        //cart.items[0].payee
        Map<String, Object> cartItemPayeeMap = new HashMap<String, Object>();
        cartItemPayeeMap.put(
                "name",
                bizEvent.getCreditor() != null ? bizEvent.getCreditor().getOfficeName() : ""
        );
        cartItemPayeeMap.put(
                "taxCode",
                bizEvent.getCreditor() != null ? bizEvent.getCreditor().getCompanyName() : ""
        );

        cartItemMap.put("refNumber", cartItemRefNumberMap);
        cartItemMap.put("debtor", cartItemDebtorMap);
        cartItemMap.put("payee", cartItemPayeeMap);
        cartItemMap.put(
                "subject",
                bizEvent.getPaymentInfo() != null ? bizEvent.getPaymentInfo().getRemittanceInformation() : ""
        );
        cartItemMap.put(
                "amount",
                bizEvent.getPaymentInfo() != null ? bizEvent.getPaymentInfo().getAmount() : ""
        );

        cartItemsArray.add(cartItemMap);

        cartMap.put("items", cartItemsArray);


        map.put("transaction", transactionMap);
        map.put("user", userMap);
        map.put("cart", cartMap);
        map.put("noticeCode", "");
        map.put("amount", 0);

        return map;
    }
}
