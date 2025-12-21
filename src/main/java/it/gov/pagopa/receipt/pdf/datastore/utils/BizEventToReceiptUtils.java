package it.gov.pagopa.receipt.pdf.datastore.utils;

import com.azure.cosmos.models.FeedResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.receipt.pdf.datastore.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.Transfer;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.enumeration.UserType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.CartItem;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventBadRequestException;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.model.MassiveRecoverResult;
import it.gov.pagopa.receipt.pdf.datastore.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.datastore.service.ReceiptCosmosService;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BizEventToReceiptUtils {

    private static final String REMITTANCE_INFORMATION_REGEX = "/TXT/(.*)";
    private static final Boolean ECOMMERCE_FILTER_ENABLED = Boolean.parseBoolean(System.getenv().getOrDefault(
            "ECOMMERCE_FILTER_ENABLED", "true"));
    private static final List<String> AUTHENTICATED_CHANNELS = Arrays.asList(System.getenv().getOrDefault(
            "AUTHENTICATED_CHANNELS", "IO,CHECKOUT,WISP,CHECKOUT_CART").split(","));
    private static final List<String> UNWANTED_REMITTANCE_INFO = Arrays.asList(System.getenv().getOrDefault(
            "UNWANTED_REMITTANCE_INFO", "pagamento multibeneficiario,pagamento bpay").split(","));
    private static final String ECOMMERCE = "CHECKOUT";


    private BizEventToReceiptUtils() {
    }

    public static Receipt getEvent(
            String eventId,
            ExecutionContext context,
            BizEventToReceiptService bizEventToReceiptService,
            BizEventCosmosClient bizEventCosmosClient,
            ReceiptCosmosService receiptCosmosService,
            Receipt receipt,
            Logger logger
    ) throws BizEventNotFoundException, BizEventBadRequestException, ReceiptNotFoundException, PDVTokenizerException, JsonProcessingException {

        BizEvent bizEvent = bizEventCosmosClient.getBizEventDocument(eventId);

        if (isBizEventInvalid(bizEvent, context, logger)) {
            throw new BizEventBadRequestException("BizEvent not valid");
        }

        if (!hasValidTotalNotice(bizEvent, context, logger)) {
            throw new BizEventBadRequestException("BizEvent has not a valid total notice");
        }

        if (receipt == null) {
            receipt = receiptCosmosService.getReceipt(eventId);
        }

        // check that the receipt is in one of the 3 manageable states: FAILED, INSERTED, and NOT_QUEUE_SENT -> if not, error
        if (receipt != null && (
                receipt.getStatus().equals(ReceiptStatusType.FAILED) ||
                receipt.getStatus().equals(ReceiptStatusType.INSERTED) ||
                receipt.getStatus().equals(ReceiptStatusType.NOT_QUEUE_SENT)
        )) {
            // recreate the receipt from the biz
            receipt = createReceipt(bizEvent, bizEventToReceiptService, logger);
            if (isReceiptStatusValid(receipt)) {
                bizEventToReceiptService.handleSaveReceipt(receipt);

                if (isReceiptStatusValid(receipt)) {
                    bizEventToReceiptService.handleSendMessageToQueue(Collections.singletonList(bizEvent), receipt);

                    return receipt;
                }
            }
        }
        return receipt;
    }

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
        BigDecimal amount = getAmount(bizEvent);
        eventData.setAmount(!amount.equals(BigDecimal.ZERO) ? formatAmount(amount.toString()) : null);

        CartItem item = new CartItem();
        item.setPayeeName(bizEvent.getCreditor() != null ? bizEvent.getCreditor().getCompanyName() : null);
        item.setSubject(getItemSubject(bizEvent));
        List<CartItem> cartItems = Collections.singletonList(item);
        eventData.setCart(cartItems);

        receipt.setEventData(eventData);
        receipt.setStatus(ReceiptStatusType.INSERTED);
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
    public static boolean isBizEventInvalid(BizEvent bizEvent, ExecutionContext context, Logger logger) {

        if (bizEvent == null) {
            logger.debug("[{}] event is null", context.getFunctionName());
            return true;
        }

        if (!BizEventStatusType.DONE.equals(bizEvent.getEventStatus())) {
            logger.debug("[{}] event with id {} discarded because in status {}",
                    context.getFunctionName(), bizEvent.getId(), bizEvent.getEventStatus());
            return true;
        }

        if (!hasValidFiscalCode(bizEvent)) {
            logger.debug("[{}] event with id {} discarded because debtor's and payer's identifiers are missing or not valid",
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

        if (!isCartMod1(bizEvent)) {
            logger.debug("[{}] event with id {} contain either an invalid amount value," +
                            " or it is a legacy cart element",
                    context.getFunctionName(), bizEvent.getId());
            return true;
        }

        return false;
    }

    private static boolean hasValidTotalNotice(BizEvent bizEvent, ExecutionContext context, Logger logger) {
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
                    return false;
                }

                if (intTotalNotice > 1) {
                    logger.error("[{}] event with id {} discarded because is part of a payment cart ({} total notice)",
                            context.getFunctionName(), bizEvent.getId(),
                            intTotalNotice);
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasValidFiscalCode(BizEvent bizEvent) {
        boolean isValidDebtor = false;
        boolean isValidPayer = false;

        if (bizEvent.getDebtor() != null && isValidFiscalCode(bizEvent.getDebtor().getEntityUniqueIdentifierValue())) {
            isValidDebtor = true;
        }
        if (isValidChannelOrigin(bizEvent)) {
            if (bizEvent.getTransactionDetails() != null && bizEvent.getTransactionDetails().getUser() != null && isValidFiscalCode(bizEvent.getTransactionDetails().getUser().getFiscalCode())) {
                isValidPayer = true;
            }
            if (bizEvent.getPayer() != null && isValidFiscalCode(bizEvent.getPayer().getEntityUniqueIdentifierValue())) {
                isValidPayer = true;
            }
        }
        return isValidDebtor || isValidPayer;
    }

    public static Integer getTotalNotice(BizEvent bizEvent, ExecutionContext context, Logger logger) {
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
                    throw e;
                }
                return intTotalNotice;
            }
        }
        return 1;
    }

    /**
     * Retrieve RemittanceInformation from BizEvent
     *
     * @param bizEvent BizEvent from which retrieve the data
     * @return the remittance information
     */
    public static String getItemSubject(BizEvent bizEvent) {
        if (
                bizEvent.getPaymentInfo() != null &&
                        bizEvent.getPaymentInfo().getRemittanceInformation() != null &&
                        !UNWANTED_REMITTANCE_INFO.contains(bizEvent.getPaymentInfo().getRemittanceInformation().toLowerCase())
        ) {
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

    public static BigDecimal getAmount(BizEvent bizEvent) {
        if (bizEvent.getTransactionDetails() != null && bizEvent.getTransactionDetails().getTransaction() != null) {
            return formatEuroCentAmount(bizEvent.getTransactionDetails().getTransaction().getGrandTotal());
        }
        if (bizEvent.getPaymentInfo() != null && bizEvent.getPaymentInfo().getAmount() != null) {
            return new BigDecimal(bizEvent.getPaymentInfo().getAmount());
        }
        return BigDecimal.ZERO;
    }

    public static BigDecimal getCartAmount(BizEvent bizEvent) {
        if (bizEvent.getTransactionDetails() != null && bizEvent.getTransactionDetails().getTransaction() != null) {
            return formatEuroCentAmount(bizEvent.getTransactionDetails().getTransaction().getGrandTotal());
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal formatEuroCentAmount(long grandTotal) {
        BigDecimal amount = new BigDecimal(grandTotal);
        BigDecimal divider = new BigDecimal(100);
        return amount.divide(divider, 2, RoundingMode.UNNECESSARY);
    }

    public static String formatAmount(String value) {
        BigDecimal valueToFormat = new BigDecimal(value);
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.ITALY);
        numberFormat.setMaximumFractionDigits(2);
        numberFormat.setMinimumFractionDigits(2);
        return numberFormat.format(valueToFormat);
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

    public static boolean isReceiptStatusValid(Receipt receipt) {
        return receipt.getStatus() != ReceiptStatusType.FAILED && receipt.getStatus() != ReceiptStatusType.NOT_QUEUE_SENT;
    }

    public static boolean isCartStatusValid(CartForReceipt cartForReceipt) {
        return cartForReceipt.getStatus() != CartStatusType.FAILED && cartForReceipt.getStatus() != CartStatusType.NOT_QUEUE_SENT;
    }

    public static boolean isValidFiscalCode(String fiscalCode) {
        if (fiscalCode != null && !fiscalCode.isEmpty()) {
            Pattern patternCF = Pattern.compile("^[A-Z]{6}[0-9LMNPQRSTUV]{2}[ABCDEHLMPRST][0-9LMNPQRSTUV]{2}[A-Z][0-9LMNPQRSTUV]{3}[A-Z]$");
            Pattern patternPIVA = Pattern.compile("/^[0-9]{11}$/");

            return patternCF.matcher(fiscalCode).find() || patternPIVA.matcher(fiscalCode).find();
        }

        return false;
    }

    /**
     * Method to check if the content comes from a legacy cart model (see https://pagopa.atlassian.net/browse/VAS-1167)
     *
     * @param bizEvent bizEvent to validate
     * @return flag to determine if it is a manageable cart, or otherwise, will return false if
     * it is considered a legacy cart content (not having a totalNotice field and having amount values != 0)
     */
    public static boolean isCartMod1(BizEvent bizEvent) {
        if (bizEvent.getPaymentInfo() != null && bizEvent.getPaymentInfo().getTotalNotice() == null) {
            return bizEvent.getTransactionDetails() != null &&
                    new BigDecimal(bizEvent.getPaymentInfo().getAmount()).subtract(
                                    formatEuroCentAmount(bizEvent.getTransactionDetails().getTransaction().getAmount()))
                            .floatValue() == 0;
        }
        return true;
    }

    public static boolean isValidChannelOrigin(BizEvent bizEvent) {
        if (bizEvent.getTransactionDetails() == null) {
            return false;
        }

        var transactionDetails = bizEvent.getTransactionDetails();
        var transaction = transactionDetails.getTransaction();
        var info = transactionDetails.getInfo();
        var user = transactionDetails.getUser();

        String origin = (transaction != null) ? transaction.getOrigin() : null;
        String clientId = (info != null) ? info.getClientId() : null;
        UserType userType = (user != null) ? user.getType() : null;

        boolean originMatches = origin != null && AUTHENTICATED_CHANNELS.contains(origin);
        boolean clientIdMatches = clientId != null && AUTHENTICATED_CHANNELS.contains(clientId);

        boolean isCheckoutOrigin = ECOMMERCE.equalsIgnoreCase(origin);
        boolean isCheckoutClientId = ECOMMERCE.equalsIgnoreCase(clientId);
        boolean isRegisteredUser = UserType.REGISTERED.equals(userType);

        if ((isCheckoutOrigin || isCheckoutClientId) && !isRegisteredUser) {
            return false;
        }

        return originMatches || clientIdMatches;
    }

    public static MassiveRecoverResult massiveRecoverByStatus(
            ExecutionContext context,
            BizEventToReceiptService bizEventToReceiptService,
            BizEventCosmosClient bizEventCosmosClient,
            ReceiptCosmosService receiptCosmosService,
            Logger logger,
            ReceiptStatusType statusType) {
        List<Receipt> receiptList = new ArrayList<>();
        int successCounter = 0;
        int errorCounter = 0;
        String continuationToken = null;
        do {
            Iterable<FeedResponse<Receipt>> feedResponseIterator =
                    receiptCosmosService.getFailedReceiptByStatus(continuationToken, 100, statusType);

            for (FeedResponse<Receipt> page : feedResponseIterator) {
                for (Receipt receipt : page.getResults()) {
                    try {
                        Receipt restored = getEvent(receipt.getEventId(), context, bizEventToReceiptService,
                                bizEventCosmosClient, receiptCosmosService, receipt, logger);
                        if (isReceiptStatusValid(restored)) {
                            receiptList.add(restored);
                            successCounter++;
                        } else {
                            errorCounter++;
                        }
                    } catch (Exception e) {
                        logger.error(e.getMessage(), e);
                        errorCounter++;
                    }
                }
                continuationToken = page.getContinuationToken();
            }
        } while (continuationToken != null);

        return MassiveRecoverResult.builder()
                .receiptList(receiptList)
                .successCounter(successCounter)
                .errorCounter(errorCounter)
                .build();
    }
}
