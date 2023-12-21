package it.gov.pagopa.receipt.pdf.datastore.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.Transfer;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.CartItem;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.service.BizEventToReceiptService;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BizEventToReceiptUtils {

    private static final String REMITTANCE_INFORMATION_REGEX = "/TXT/(.*)";
    private static final Boolean ECOMMERCE_FILTER_ENABLED = Boolean.parseBoolean(System.getenv().getOrDefault("ECOMMERCE_FILTER_ENABLED", "true"));
    private static final String ECOMMERCE = "CHECKOUT";

    /**
     * Creates a new instance of Receipt, using the tokenizer service to mask the PII, based on
     * the provided BizEvent
     *
     * @param bizEvent instance of BizEvent
     * @param service  implementation of the BizEventToReceipt service to use
     * @return generated instance of Receipt
     */
    public static Receipt createReceipt(BizEvent bizEvent, BizEventToReceiptService service, Logger logger) {
        Receipt receipt = new Receipt();

        // Insert biz-event data into receipt
        receipt.setId(bizEvent.getId() + UUID.randomUUID());
        receipt.setEventId(bizEvent.getId());

        EventData eventData = new EventData();

        try {
            service.tokenizeFiscalCodes(bizEvent, receipt, eventData);
        } catch (Exception e) {
            logger.error("Error tokenizing receipt with bizEventId {}", bizEvent.getId(), e);
            receipt.setStatus(ReceiptStatusType.FAILED);
            return receipt;
        }

        eventData.setTransactionCreationDate(
                service.getTransactionCreationDate(bizEvent));
        eventData.setAmount(bizEvent.getPaymentInfo() != null ?
                bizEvent.getPaymentInfo().getAmount() : null);

        CartItem item = new CartItem();
        item.setPayeeName(bizEvent.getCreditor() != null ? bizEvent.getCreditor().getCompanyName() : null);
        item.setSubject(getItemSubject(bizEvent));
        List<CartItem> cartItems = Collections.singletonList(item);
        eventData.setCart(cartItems);

        receipt.setEventData(eventData);
        return receipt;
    }

    /**
     * Checks if the instance of Biz Event is in status DONE and contains all the required information to process
     * in the receipt generation
     *
     * @param bizEvent BizEvent to validate
     * @param context  Function context
     * @param logger   Function logger
     * @return boolean to determine if the proposed event is invalid
     */
    public static boolean isBizEventInvalid(BizEvent bizEvent, ExecutionContext context, BizEventToReceiptService service, Logger logger) {

        if (bizEvent == null) {
            logger.debug("[{}] event is null", context.getFunctionName());
            return true;
        }

        if (!bizEvent.getEventStatus().equals(BizEventStatusType.DONE)) {
            logger.debug("[{}] event with id {} discarded because in status {}",
                    context.getFunctionName(), bizEvent.getId(), bizEvent.getEventStatus());
            return true;
        }

        if (bizEvent.getDebtor().getEntityUniqueIdentifierValue() == null ||
                bizEvent.getDebtor().getEntityUniqueIdentifierValue().equals("ANONIMO")) {
            logger.debug("[{}] event with id {} discarded because debtor identifier is missing or ANONIMO",
                    context.getFunctionName(), bizEvent.getId());
            return true;
        }

        if (Boolean.TRUE.equals(ECOMMERCE_FILTER_ENABLED)
                && bizEvent.getTransactionDetails() != null
                && bizEvent.getTransactionDetails().getInfo() != null
                && ECOMMERCE.equals(bizEvent.getTransactionDetails().getInfo().getClientId())
        ) {
            logger.debug("[{}] event with id {} discarded because from e-commerce {}",
                    context.getFunctionName(), bizEvent.getId(), bizEvent.getTransactionDetails().getInfo().getClientId());
            return true;
        }

        try {
            Receipt receipt = service.getReceipt(bizEvent.getId());
            logger.debug("[{}] event with id {} discarded because already processed, receipt already exist with id {}",
                    context.getFunctionName(), bizEvent.getId(), receipt.getId());
            return true;
        } catch (ReceiptNotFoundException ignored) {
            // the receipt does not exist
        }

        if (bizEvent.getPaymentInfo() != null) {
            String totalNotice = bizEvent.getPaymentInfo().getTotalNotice();

            if (totalNotice != null) {
                int intTotalNotice;

                try {
                    intTotalNotice = Integer.parseInt(totalNotice);

                } catch (NumberFormatException e) {
                    logger.error("[{}] event with id {} discarded because has an invalid total notice value: {}",
                            context.getFunctionName(), bizEvent.getId(),
                            totalNotice,
                            e);
                    return true;
                }

                if (intTotalNotice > 1) {
                    logger.debug("[{}] event with id {} discarded because is part of a payment cart ({} total notice)",
                            context.getFunctionName(), bizEvent.getId(),
                            intTotalNotice);
                    return true;
                }
            }
        }

        return false;
    }

    public static void tokenizeReceipt(BizEventToReceiptService service, BizEvent bizEvent, Receipt receipt)
            throws PDVTokenizerException, JsonProcessingException {
        if (receipt.getEventData() == null) {
            EventData eventData = new EventData();
            receipt.setEventData(eventData);
            eventData.setTransactionCreationDate(
                    service.getTransactionCreationDate(bizEvent));
            eventData.setAmount(bizEvent.getPaymentInfo() != null ?
                    bizEvent.getPaymentInfo().getAmount() : null);

            CartItem item = new CartItem();
            item.setPayeeName(bizEvent.getCreditor() != null ? bizEvent.getCreditor().getCompanyName() : null);
            item.setSubject(getItemSubject(bizEvent));
            List<CartItem> cartItems = Collections.singletonList(item);
            eventData.setCart(cartItems);
        }
        service.tokenizeFiscalCodes(bizEvent, receipt, receipt.getEventData());
    }

    /**
     * Retrieve RemittanceInformation from BizEvent
     *
     * @param bizEvent BizEvent from which retrieve the data
     * @return the remittance information
     */
    private static String getItemSubject(BizEvent bizEvent) {
        if (bizEvent.getPaymentInfo() != null && bizEvent.getPaymentInfo().getRemittanceInformation() != null) {
            return bizEvent.getPaymentInfo().getRemittanceInformation();
        }
        List<Transfer> transferList = bizEvent.getTransferList();
        if (transferList != null && !transferList.isEmpty()) {
            double amount = 0;
            String remittanceInformation = null;
            for (Transfer transfer : transferList) {
                double transferAmount;
                try {
                    transferAmount = Double.parseDouble(transfer.getAmount());
                } catch (Exception ignored) {
                    continue;
                }
                if (amount < transferAmount) {
                    amount = transferAmount;
                    remittanceInformation = transfer.getRemittanceInformation();
                }
            }
            return formatRemittanceInformation(remittanceInformation);
        }
        return null;
    }

    private static String formatRemittanceInformation(String remittanceInformation) {
        if (remittanceInformation != null) {
            Pattern pattern = Pattern.compile(REMITTANCE_INFORMATION_REGEX);
            Matcher matcher = pattern.matcher(remittanceInformation);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return remittanceInformation;
    }

    private BizEventToReceiptUtils() {}
}
