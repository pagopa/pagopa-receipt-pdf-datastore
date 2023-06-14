package it.gov.pagopa.receipt.pdf.datastore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.receipt.pdf.datastore.client.PdfEngineClient;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.PdfEngineClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.model.request.PdfEngineRequest;
import it.gov.pagopa.receipt.pdf.datastore.utils.ObjectMapperUtils;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class GenerateReceipt {

    private ReceiptCosmosClientImpl receiptCosmosClient;

    private PdfEngineClient pdfEngineClient;

    private final int maxNumberRetry = Integer.parseInt(System.getenv().getOrDefault("COSMOS_RECEIPT_QUEUE_MAX_RETRY", "5"));

    private final String ocpApimSubsKey = System.getenv("OCP_APIM_SUBSCRIPTION_KEY");

    GenerateReceipt(ReceiptCosmosClientImpl receiptCosmosClient, PdfEngineClient pdfEngineClient) {
        this.receiptCosmosClient = receiptCosmosClient;
        this.pdfEngineClient = pdfEngineClient;
    }

    GenerateReceipt() {
        this.receiptCosmosClient = new ReceiptCosmosClientImpl();
        this.pdfEngineClient = new PdfEngineClientImpl();
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

            int statusDebtorGen = 0;
            int statusPayerGen = 0;

            //Generate PDFs for the needed fiscal codes
            if (payerDebtorEqual) {
                if (receipt.getMdAttach().getName().isEmpty()) {
                    statusDebtorGen = handleReceiptPDFGeneration(bizEvent, true);
                }
            } else {
                //verify the debtor pdf hasn't already been generated
                if (receipt.getMdAttach().getName().isEmpty()) {
                    statusDebtorGen = handleReceiptPDFGeneration(bizEvent, false);
                }
                //verify the payer pdf hasn't already been generated
                if (receipt.getMdAttachPayer().getName().isEmpty()) {
                    statusPayerGen = handleReceiptPDFGeneration(bizEvent, true);
                }
            }

            //Verify pdf generation success
            if (statusDebtorGen == 200 && statusPayerGen == 200) {
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
                int errorStatusCode = statusDebtorGen == 200 ? statusPayerGen : statusDebtorGen;
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

    private int handleReceiptPDFGeneration(BizEvent bizEvent, boolean completeTemplate) {
        int pdfEngineStatusCode = 0;
        PdfEngineRequest request = new PdfEngineRequest();

        String templateFileName = completeTemplate ? "complete_template.zip" : "partial_template.zip";
        File htmlTemplate = new File("src/main/resources/"+templateFileName);
        request.setTemplate(htmlTemplate);
        request.setData(convertReceiptToData(bizEvent).toString());
        request.setApplySignature(false);

        //Response pdfResponse = pdfEngineClient.generatePDF();

        return 0; //pdfResponse.status();
    }

    private Map<String, Object> convertReceiptToData(BizEvent bizEvent) {
        Map<String, Object> map = new HashMap<String, Object>();

        //transaction
        Map<String, Object> transactionMap = new HashMap<String, Object>();
        //transaction.psp
        Map<String, Object> transactionPspMap = new HashMap<String, Object>();
        transactionPspMap.put("name", bizEvent.getTransactionDetails().getTransaction().getPsp().getBusinessName());
        //transaction.psp.fee
        Map<String, Object> transactionPspFeeMap = new HashMap<String, Object>();
        transactionPspFeeMap.put("amount", bizEvent.getTransactionDetails().getTransaction().getFee());
        transactionPspMap.put("fee", transactionPspFeeMap);
        //transaction.paymentMethod
        Map<String, String> transactionPaymentMethod = new HashMap<String, String>();
        transactionPaymentMethod.put("name", bizEvent.getPaymentInfo().getPaymentMethod());
        transactionPaymentMethod.put("logo", bizEvent.getTransactionDetails().getWallet().getInfo().getBrand());
        transactionPaymentMethod.put("accountHolder", bizEvent.getTransactionDetails().getWallet().getInfo().getHolder());
        transactionPaymentMethod.put("extraFee", "false");

        transactionMap.put("id", bizEvent.getTransactionDetails().getTransaction().getIdTransaction());
        transactionMap.put("timestamp", bizEvent.getPaymentInfo().getPaymentDateTime());
        transactionMap.put("amount", bizEvent.getPaymentInfo().getAmount());
        transactionMap.put("psp", transactionPspMap);
        transactionMap.put("rrn", bizEvent.getTransactionDetails().getTransaction().getRrn());
        transactionMap.put("paymentMethod", transactionPaymentMethod);
        transactionMap.put("authCode", bizEvent.getTransactionDetails().getTransaction().getAuthorizationCode());

        //user
        Map<String, Object> userMap = new HashMap<String, Object>();
        //user.data
        Map<String, Object> userDataMap = new HashMap<String, Object>();
        userDataMap.put("firstName", bizEvent.getTransactionDetails().getUser().getFullName());
        userDataMap.put("lastName", "x");
        userDataMap.put("taxCode", bizEvent.getTransactionDetails().getUser().getFiscalCode());

        userMap.put("data", userDataMap);
        userMap.put("mail", bizEvent.getDebtor().getEMail());

        //cart
        Map<String, Object> cartMap = new HashMap<String, Object>();
        //cart.items
        ArrayList<Object> cartItemsArray = new ArrayList<>();
        //cart.items[0]
        Map<String, Object> cartItemMap = new HashMap<String, Object>();
        //cart.items[0].refNumber
        Map<String, Object> cartItemRefNumberMap = new HashMap<String, Object>();
        cartItemRefNumberMap.put("type", "CODICE AVVISO");
        cartItemRefNumberMap.put("value", bizEvent.getDebtorPosition().getIuv());
        //cart.items[0].debtor
        Map<String, Object> cartItemDebtorMap = new HashMap<String, Object>();
        cartItemDebtorMap.put("fullName", bizEvent.getDebtor().getFullName());
        cartItemDebtorMap.put("taxCode", bizEvent.getDebtor().getEntityUniqueIdentifierType());
        //cart.items[0].payee
        Map<String, Object> cartItemPayeeMap = new HashMap<String, Object>();
        cartItemPayeeMap.put("name", bizEvent.getCreditor().getOfficeName());
        cartItemPayeeMap.put("taxCode", bizEvent.getCreditor().getCompanyName());

        cartItemMap.put("refNumber", cartItemRefNumberMap);
        cartItemMap.put("debtor", cartItemDebtorMap);
        cartItemMap.put("payee", cartItemPayeeMap);
        cartItemMap.put("subject", bizEvent.getPaymentInfo().getRemittanceInformation());
        cartItemMap.put("amount", bizEvent.getPaymentInfo().getAmount());

        cartItemsArray.add(cartItemMap);

        cartMap.put("items", cartItemsArray);


        map.put("transaction", transactionMap);
        map.put("cart", cartMap);
        map.put("noticeCode", "x");
        map.put("amount", 0);

        return map;
    }
}
