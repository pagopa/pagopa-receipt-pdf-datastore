package it.gov.pagopa.receipt.pdf.datastore.service.impl;

import com.azure.core.http.rest.Response;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.FeedResponse;
import com.azure.storage.queue.models.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.receipt.pdf.datastore.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptQueueClient;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.CartReceiptsCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.datastore.service.PDVTokenizerServiceRetryWrapper;
import it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils;
import it.gov.pagopa.receipt.pdf.datastore.utils.ObjectMapperUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.Set;

public class BizEventToReceiptServiceImpl implements BizEventToReceiptService {

    public static final String CF_UNKNOWN = "ANONIMO";
    private final Logger logger = LoggerFactory.getLogger(BizEventToReceiptServiceImpl.class);
    private static final String[] VALID_CHANNEL_ORIGIN = System.getenv().getOrDefault("VALID_CHANNEL_ORIGIN", "").split(",");

    private final PDVTokenizerServiceRetryWrapper pdvTokenizerService;
    private final ReceiptCosmosClient receiptCosmosClient;

    private final CartReceiptsCosmosClient cartReceiptsCosmosClient;
    private final BizEventCosmosClient bizEventCosmosClient;

    private final ReceiptQueueClient queueClient;

    public BizEventToReceiptServiceImpl() {
        this.pdvTokenizerService = new PDVTokenizerServiceRetryWrapperImpl();
        this.receiptCosmosClient = ReceiptCosmosClientImpl.getInstance();
        this.cartReceiptsCosmosClient = CartReceiptsCosmosClientImpl.getInstance();
        this.bizEventCosmosClient = BizEventCosmosClientImpl.getInstance();
        this.queueClient = ReceiptQueueClientImpl.getInstance();
    }

    public BizEventToReceiptServiceImpl(PDVTokenizerServiceRetryWrapper pdvTokenizerService,
                                        ReceiptCosmosClient receiptCosmosClient,
                                        CartReceiptsCosmosClient cartReceiptsCosmosClient,
                                        BizEventCosmosClient bizEventCosmosClient,
                                        ReceiptQueueClient queueClient) {
        this.pdvTokenizerService = pdvTokenizerService;
        this.receiptCosmosClient = receiptCosmosClient;
        this.cartReceiptsCosmosClient = cartReceiptsCosmosClient;
        this.bizEventCosmosClient = bizEventCosmosClient;
        this.queueClient = queueClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleSendMessageToQueue(List<BizEvent> bizEventList, Receipt receipt) {
        //Encode biz-event to base64 string
        String messageText = Base64.getMimeEncoder().encodeToString(
                Objects.requireNonNull(ObjectMapperUtils.writeValueAsString(bizEventList)).getBytes(StandardCharsets.UTF_8));

        //Add message to the queue
        int statusCode;
        try {
            Response<SendMessageResult> sendMessageResult = queueClient.sendMessageToQueue(messageText);
            statusCode = sendMessageResult.getStatusCode();
        } catch (Exception e) {
            statusCode = ReasonErrorCode.ERROR_QUEUE.getCode();
            if (bizEventList.size() == 1) {
                logger.error("Sending BizEvent with id {} to queue failed", receipt.getEventId(), e);
            } else {
                logger.error("Failed to enqueue cart with id {}", receipt.getEventId(), e);
            }
        }

        if (statusCode != HttpStatus.CREATED.value()) {
            String errorString = String.format(
                    "[BizEventToReceiptService] Error sending message to queue for receipt with eventId %s",
                    receipt.getEventId());
            handleError(receipt, ReceiptStatusType.NOT_QUEUE_SENT, errorString, statusCode);
            //Error info
            logger.error(errorString);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Receipt getReceipt(String bizEventId) throws ReceiptNotFoundException {
        Receipt receipt;
        try {
            receipt = receiptCosmosClient.getReceiptDocument(bizEventId);
        } catch (ReceiptNotFoundException e) {
            String errorMsg = String.format("Receipt not found with the biz-event id %s", bizEventId);
            throw new ReceiptNotFoundException(errorMsg, e);
        }

        if (receipt == null) {
            String errorMsg = String.format("Receipt retrieved with the biz-event id %s is null", bizEventId);
            throw new ReceiptNotFoundException(errorMsg);
        }
        return receipt;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleSaveReceipt(Receipt receipt) {
        int statusCode;

        try {
            receipt.setStatus(ReceiptStatusType.INSERTED);
            receipt.setInserted_at(System.currentTimeMillis());
            CosmosItemResponse<Receipt> response = receiptCosmosClient.saveReceipts(receipt);

            statusCode = response.getStatusCode();
        } catch (Exception e) {
            statusCode = ReasonErrorCode.ERROR_COSMOS.getCode();
            logger.error("Save receipt with eventId {} on cosmos failed", receipt.getEventId(), e);
        }

        if (statusCode != (HttpStatus.CREATED.value())) {
            String errorString = String.format(
                    "[BizEventToReceiptService] Error saving receipt to cosmos for receipt with eventId %s, cosmos client responded with status %s",
                    receipt.getEventId(), statusCode);
            handleError(receipt, ReceiptStatusType.FAILED, errorString, statusCode);
            //Error info
            logger.error(errorString);
        }
    }

    /**
     * Handles errors for queue and cosmos and updates receipt's status accordingly
     *
     * @param receipt Receipt to update
     */
    private void handleError(Receipt receipt, ReceiptStatusType statusType, String errorMessage, int errorCode) {
        receipt.setStatus(statusType);
        ReasonError reasonError = new ReasonError(errorCode, errorMessage);
        receipt.setReasonErr(reasonError);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTransactionCreationDate(BizEvent bizEvent) {
        if (bizEvent.getTransactionDetails() != null && bizEvent.getTransactionDetails().getTransaction() != null) {
            return bizEvent.getTransactionDetails().getTransaction().getCreationDate();

        } else if (bizEvent.getPaymentInfo() != null) {
            return bizEvent.getPaymentInfo().getPaymentDateTime();
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tokenizeFiscalCodes(BizEvent bizEvent, Receipt receipt, EventData eventData) throws JsonProcessingException, PDVTokenizerException {
        try {
            //Tokenize Debtor
            if (bizEvent.getDebtor() != null && bizEvent.getDebtor().getEntityUniqueIdentifierValue() != null) {
                eventData.setDebtorFiscalCode(CF_UNKNOWN.equals(bizEvent.getDebtor().getEntityUniqueIdentifierValue()) ?
                        bizEvent.getDebtor().getEntityUniqueIdentifierValue() :
                        pdvTokenizerService.generateTokenForFiscalCodeWithRetry(bizEvent.getDebtor().getEntityUniqueIdentifierValue())
                );
            }
            //Tokenize Payer
            if (isValidChannelOrigin(bizEvent)) {
                if (bizEvent.getPayer() != null && bizEvent.getPayer().getEntityUniqueIdentifierValue() != null) {
                    eventData.setPayerFiscalCode(
                            pdvTokenizerService.generateTokenForFiscalCodeWithRetry(bizEvent.getPayer().getEntityUniqueIdentifierValue())
                    );
                } else if (
                        bizEvent.getTransactionDetails().getUser() != null &&
                                bizEvent.getTransactionDetails().getUser().getFiscalCode() != null
                ) {
                    eventData.setPayerFiscalCode(
                            pdvTokenizerService.generateTokenForFiscalCodeWithRetry(
                                    bizEvent.getTransactionDetails().getUser().getFiscalCode()));
                }
            }
        } catch (PDVTokenizerException e) {
            handleTokenizerException(receipt, e.getMessage(), e.getStatusCode());
            throw e;
        } catch (JsonProcessingException e) {
            handleTokenizerException(receipt, e.getMessage(), ReasonErrorCode.ERROR_PDV_MAPPING.getCode());
            throw e;
        }
    }

    private boolean isValidChannelOrigin(BizEvent bizEvent) {
        if (bizEvent.getTransactionDetails() != null) {
            if (
                    bizEvent.getTransactionDetails().getTransaction() != null &&
                            bizEvent.getTransactionDetails().getTransaction().getOrigin() != null &&
                            Arrays.asList(VALID_CHANNEL_ORIGIN).contains(bizEvent.getTransactionDetails().getTransaction().getOrigin())
            ) {
                return true;
            }
            if (
                    bizEvent.getTransactionDetails().getInfo() != null &&
                            bizEvent.getTransactionDetails().getInfo().getClientId() != null &&
                            Arrays.asList(VALID_CHANNEL_ORIGIN).contains(bizEvent.getTransactionDetails().getInfo().getClientId())
            ) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void handleSaveCart(BizEvent bizEvent) {
        String transactionId = bizEvent.getTransactionDetails().getTransaction().getTransactionId();
        CartForReceipt cartForReceipt;
        Set<String> cartPaymentId = new HashSet<>();
        try {
            cartForReceipt = cartReceiptsCosmosClient.getCartItem(transactionId);
            if (cartForReceipt == null) {
                throw new CartNotFoundException("Missing Cart");
            }
        } catch (CartNotFoundException ignore) {
            cartPaymentId.add(bizEvent.getId());
            cartForReceipt = CartForReceipt.builder()
                    .id(transactionId)
                    .status(CartStatusType.INSERTED)
                    .cartPaymentId(cartPaymentId)
                    .totalNotice(BizEventToReceiptUtils.getTotalNotice(bizEvent, null, null))
                    .inserted_at(System.currentTimeMillis())
                    .build();
            cartReceiptsCosmosClient.saveCart(cartForReceipt);

            return;
        }

        cartPaymentId = cartForReceipt.getCartPaymentId();
        cartPaymentId.add(bizEvent.getId());
        cartForReceipt.setCartPaymentId(cartPaymentId);
        cartReceiptsCosmosClient.updateCart(cartForReceipt);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BizEvent> getCartBizEvents(String cartId) {
        List<BizEvent> bizEventList = new ArrayList<>();
        String continuationToken = null;
        do {
            Iterable<FeedResponse<BizEvent>> feedResponseIterator =
                    this.bizEventCosmosClient.getAllBizEventDocument(cartId, continuationToken, 100);

            for (FeedResponse<BizEvent> page : feedResponseIterator) {
                bizEventList.addAll(page.getResults());
                continuationToken = page.getContinuationToken();
            }
        } while (continuationToken != null);
        return bizEventList;
    }

    /**
     * Handles errors for PDV tokenizer and updates receipt's status accordingly
     *
     * @param receipt      Receipt to update
     * @param errorMessage Message to save
     * @param statusCode   StatusCode to save
     */
    private void handleTokenizerException(Receipt receipt, String errorMessage, int statusCode) {
        receipt.setStatus(ReceiptStatusType.FAILED);
        ReasonError reasonError = new ReasonError(statusCode, errorMessage);
        receipt.setReasonErr(reasonError);
    }
}
