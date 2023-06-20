package it.gov.pagopa.receipt.pdf.datastore.utils;

import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.Info;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.Transaction;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.TransactionDetails;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReceiptMetadata;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.model.PdfGeneration;
import it.gov.pagopa.receipt.pdf.datastore.model.PdfMetadata;
import org.apache.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ReceiptPdfUtils {

    private static final String KEY_AMOUNT = "amount";
    private static final String KEY_TAX_CODE = "taxCode";

    public static final int maxNumberRetry = Integer.parseInt(System.getenv().getOrDefault("COSMOS_RECEIPT_QUEUE_MAX_RETRY", "5"));

    /**
     * Hide from public usage.
     */
    private ReceiptPdfUtils() {
    }

    public static void addPdfsMetadataToReceipt(Receipt receipt, PdfGeneration responseGen) {
        PdfMetadata debtorMetadata = responseGen.getDebtorMetadata();
        PdfMetadata payerMetadata = responseGen.getPayerMetadata();

        if (debtorMetadata != null && debtorMetadata.getStatusCode() == HttpStatus.SC_OK) {
            ReceiptMetadata receiptMetadata = new ReceiptMetadata();
            receiptMetadata.setName(debtorMetadata.getDocumentName());
            receiptMetadata.setUrl(debtorMetadata.getDocumentUrl());

            receipt.setMdAttach(receiptMetadata);

        }

        if (payerMetadata != null && payerMetadata.getStatusCode() == HttpStatus.SC_OK) {
            ReceiptMetadata receiptMetadata = new ReceiptMetadata();
            receiptMetadata.setName(payerMetadata.getDocumentName());
            receiptMetadata.setUrl(payerMetadata.getDocumentUrl());


            receipt.setMdAttachPayer(receiptMetadata);
        }
    }

    public static void verifyPdfGeneration(String message, OutputBinding<String> requeueMessage, Logger logger, Receipt receipt, boolean payerDebtorEqual, PdfGeneration pdfGeneration) {
        PdfMetadata responseDebtorGen = pdfGeneration.getDebtorMetadata();
        PdfMetadata responsePayerGen = pdfGeneration.getPayerMetadata();

        if (responseDebtorGen.getStatusCode() == HttpStatus.SC_OK && (payerDebtorEqual || responsePayerGen.getStatusCode() == HttpStatus.SC_OK)) {
            receipt.setStatus(ReceiptStatusType.GENERATED);
        } else {
            if (receipt.getNumRetry() > maxNumberRetry) {
                receipt.setStatus(ReceiptStatusType.FAILED);
            } else {
                receipt.setStatus(ReceiptStatusType.RETRY);
            }

            int errorStatusCode = responseDebtorGen.getStatusCode() == HttpStatus.SC_OK && responsePayerGen != null ? responsePayerGen.getStatusCode() : responseDebtorGen.getStatusCode();
            String errorMessage = responseDebtorGen.getErrorMessage() == null && responsePayerGen != null ? responsePayerGen.getErrorMessage() : responseDebtorGen.getErrorMessage();
            ReasonError reasonError = new ReasonError(errorStatusCode, errorMessage);
            receipt.setReasonErr(reasonError);
            receipt.setNumRetry(receipt.getNumRetry() + 1);

            requeueMessage.setValue(message);
            String logMessage = "Error generating PDF at " + LocalDateTime.now() + " : " + errorMessage;
            logger.severe(logMessage);
        }
    }

    public static Map<String, Object> convertReceiptToPdfData(BizEvent bizEvent) {
        Map<String, Object> map = new HashMap<>();

        TransactionDetails transactionDetails = bizEvent.getTransactionDetails();
        Transaction transaction = transactionDetails != null ? transactionDetails.getTransaction() : null;
        Info transactionInfo = transactionDetails != null && transactionDetails.getWallet() != null
                ? transactionDetails.getWallet().getInfo() : null;

        //transaction
        Map<String, Object> transactionMap = getTransactionMap(bizEvent, transaction, transactionInfo);

        //user
        Map<String, Object> userMap = getUserMap(bizEvent, transactionDetails);

        //cart
        Map<String, Object> cartMap = getCartMap(bizEvent);


        map.put("transaction", transactionMap);
        map.put("user", userMap);
        map.put("cart", cartMap);
        map.put("noticeCode", "");
        map.put(KEY_AMOUNT, 0);

        return map;
    }

    private static Map<String, Object> getTransactionMap(BizEvent bizEvent, Transaction transaction, Info transactionInfo) {
        Map<String, Object> transactionMap = new HashMap<>();
        //transaction.psp
        Map<String, Object> transactionPspMap = new HashMap<>();
        transactionPspMap.put(
                "name",
                transaction != null && transaction.getPsp() != null ? transaction.getPsp().getBusinessName() : ""
        );
        //transaction.psp.fee
        Map<String, Object> transactionPspFeeMap = new HashMap<>();
        transactionPspFeeMap.put(
                KEY_AMOUNT,
                transaction != null ? transaction.getFee() : ""
        );
        transactionPspMap.put(
                "fee",
                transactionPspFeeMap
        );
        //transaction.paymentMethod
        Map<String, String> transactionPaymentMethod = new HashMap<>();
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
                KEY_AMOUNT,
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
        return transactionMap;
    }

    private static Map<String, Object> getUserMap(BizEvent bizEvent, TransactionDetails transactionDetails) {
        Map<String, Object> userMap = new HashMap<>();
        //user.data
        Map<String, Object> userDataMap = new HashMap<>();
        userDataMap.put(
                "firstName",
                transactionDetails != null && transactionDetails.getUser() != null ? transactionDetails.getUser().getFullName() : ""
        );
        userDataMap.put(
                "lastName",
                ""
        );
        userDataMap.put(
                KEY_TAX_CODE,
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
        return userMap;
    }

    private static Map<String, Object> getCartMap(BizEvent bizEvent) {
        //cart
        Map<String, Object> cartMap = new HashMap<>();
        //cart.items
        ArrayList<Object> cartItemsArray = new ArrayList<>();
        //cart.items[0]
        Map<String, Object> cartItemMap = new HashMap<>();
        //cart.items[0].refNumber
        Map<String, Object> cartItemRefNumberMap = new HashMap<>();
        cartItemRefNumberMap.put(
                "type",
                "CODICE AVVISO"
        );
        cartItemRefNumberMap.put(
                "value",
                bizEvent.getDebtorPosition() != null ? bizEvent.getDebtorPosition().getIuv() : "")
        ;
        //cart.items[0].debtor
        Map<String, Object> cartItemDebtorMap = new HashMap<>();
        cartItemDebtorMap.put(
                "fullName",
                bizEvent.getDebtor() != null ? bizEvent.getDebtor().getFullName() : ""
        );
        cartItemDebtorMap.put(
                KEY_TAX_CODE,
                bizEvent.getDebtor() != null ? bizEvent.getDebtor().getEntityUniqueIdentifierType() : ""
        );
        //cart.items[0].payee
        Map<String, Object> cartItemPayeeMap = new HashMap<>();
        cartItemPayeeMap.put(
                "name",
                bizEvent.getCreditor() != null ? bizEvent.getCreditor().getOfficeName() : ""
        );
        cartItemPayeeMap.put(
                KEY_TAX_CODE,
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
                KEY_AMOUNT,
                bizEvent.getPaymentInfo() != null ? bizEvent.getPaymentInfo().getAmount() : ""
        );

        cartItemsArray.add(cartItemMap);

        cartMap.put("items", cartItemsArray);
        return cartMap;
    }
}
