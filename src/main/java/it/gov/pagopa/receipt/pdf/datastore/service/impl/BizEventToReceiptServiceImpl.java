package it.gov.pagopa.receipt.pdf.datastore.service.impl;

import com.azure.core.http.rest.Response;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.FeedResponse;
import com.azure.storage.queue.models.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.receipt.pdf.datastore.client.*;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.*;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartPayment;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.Payload;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartConcurrentUpdateException;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.datastore.service.PDVTokenizerServiceRetryWrapper;
import it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils;
import it.gov.pagopa.receipt.pdf.datastore.utils.ObjectMapperUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils.*;

public class BizEventToReceiptServiceImpl implements BizEventToReceiptService {

    public static final String FISCAL_CODE_ANONYMOUS = "ANONIMO";
    private final Logger logger = LoggerFactory.getLogger(BizEventToReceiptServiceImpl.class);

    private final PDVTokenizerServiceRetryWrapper pdvTokenizerService;
    private final ReceiptCosmosClient receiptCosmosClient;

    private final CartReceiptsCosmosClient cartReceiptsCosmosClient;
    private final BizEventCosmosClient bizEventCosmosClient;

    private final ReceiptQueueClient queueClient;
    private final CartQueueClient cartQueueClient;

    public BizEventToReceiptServiceImpl() {
        this.pdvTokenizerService = new PDVTokenizerServiceRetryWrapperImpl();
        this.receiptCosmosClient = ReceiptCosmosClientImpl.getInstance();
        this.cartReceiptsCosmosClient = CartReceiptsCosmosClientImpl.getInstance();
        this.bizEventCosmosClient = BizEventCosmosClientImpl.getInstance();
        this.queueClient = ReceiptQueueClientImpl.getInstance();
        this.cartQueueClient = CartQueueClientImpl.getInstance();
    }

    public BizEventToReceiptServiceImpl(PDVTokenizerServiceRetryWrapper pdvTokenizerService,
                                        ReceiptCosmosClient receiptCosmosClient,
                                        CartReceiptsCosmosClient cartReceiptsCosmosClient,
                                        BizEventCosmosClient bizEventCosmosClient,
                                        ReceiptQueueClient queueClient,
                                        CartQueueClientImpl cartQueueClient) {
        this.pdvTokenizerService = pdvTokenizerService;
        this.receiptCosmosClient = receiptCosmosClient;
        this.cartReceiptsCosmosClient = cartReceiptsCosmosClient;
        this.bizEventCosmosClient = bizEventCosmosClient;
        this.queueClient = queueClient;
        this.cartQueueClient = cartQueueClient;
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
                logger.error("Sending BizEvent with id {} to queue failed", bizEventList.get(0).getId(), e);
            } else {
                logger.error("Failed to enqueue cart with id {}",
                        bizEventList.get(0).getTransactionDetails().getTransaction().getIdTransaction(), e);
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

    @Override
    public void handleSendCartMessageToQueue(List<BizEvent> bizEventList, CartForReceipt cartForReceipt) {
        //Encode biz-event to base64 string
        String messageText = Base64.getMimeEncoder().encodeToString(
                Objects.requireNonNull(ObjectMapperUtils.writeValueAsString(bizEventList)).getBytes(StandardCharsets.UTF_8));

        //Add message to the queue
        int statusCode;
        try {
            Response<SendMessageResult> sendMessageResult = cartQueueClient.sendMessageToQueue(messageText);
            statusCode = sendMessageResult.getStatusCode();
        } catch (Exception e) {
            statusCode = ReasonErrorCode.ERROR_QUEUE.getCode();
            logger.error("Failed to enqueue cart with id {}", cartForReceipt.getEventId(), e);
        }

        if (statusCode != HttpStatus.CREATED.value()) {
            String errorString = String.format(
                    "[BizEventToReceiptService] Error sending message to queue for cartForReceipt with eventId %s",
                    cartForReceipt.getEventId());
            cartForReceipt.setStatus(CartStatusType.NOT_QUEUE_SENT);
            ReasonError reasonError = new ReasonError(statusCode, errorString);
            cartForReceipt.setReasonErr(reasonError);
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
            eventData.setDebtorFiscalCode(tokenizerDebtorFiscalCode(bizEvent));
            //Tokenize Payer
            eventData.setPayerFiscalCode(tokenizerPayerFiscalCode(bizEvent));
        } catch (PDVTokenizerException e) {
            handleTokenizerException(receipt, e.getMessage(), e.getStatusCode());
            throw e;
        } catch (JsonProcessingException e) {
            handleTokenizerException(receipt, e.getMessage(), ReasonErrorCode.ERROR_PDV_MAPPING.getCode());
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CartForReceipt buildCartForReceipt(BizEvent bizEvent) {
        CartForReceipt cartForReceipt = new CartForReceipt();
        String transactionId = bizEvent.getTransactionDetails().getTransaction().getTransactionId();
        try {
            cartForReceipt = findCart(transactionId);
            if (cartForReceipt == null) {
                // if cart not found create a new one
                List<CartPayment> cartItems = new ArrayList<>();
                cartItems.add(buildCartPayment(bizEvent));
                cartForReceipt = buildCart(bizEvent, transactionId, cartItems);
            } else {
                // if cart found update it with the new cart item
                List<CartPayment> cartItems = cartForReceipt.getPayload().getCart();
                cartItems.add(buildCartPayment(bizEvent));
                if (cartItems.size() == cartForReceipt.getPayload().getTotalNotice()) {
                    // if all items have been added to the cart set status to INSERTED
                    cartForReceipt.setStatus(CartStatusType.INSERTED);
                    cartForReceipt.setInserted_at(System.currentTimeMillis());
                }
            }
            return cartForReceipt;
        } catch (PDVTokenizerException e) {
            return buildCartWithFailedStatus(transactionId, cartForReceipt, e.getStatusCode(), e.getMessage(), e);
        } catch (JsonProcessingException e) {
            return buildCartWithFailedStatus(transactionId, cartForReceipt, ReasonErrorCode.ERROR_PDV_MAPPING.getCode(), e.getMessage(), e);
        } catch (Exception e) {
            return buildCartWithFailedStatus(transactionId, cartForReceipt, ReasonErrorCode.GENERIC_ERROR.getCode(), e.getMessage(), e);
        }

    }

    /**
     * {@inheritDoc}
     */
    public CartForReceipt buildCartFromBizEventList(List<BizEvent> bizEventList) throws PDVTokenizerException, JsonProcessingException {
        List<CartPayment> cartItems = new ArrayList<>();
        for (BizEvent bizEvent : bizEventList) {
            cartItems.add(buildCartPayment(bizEvent));
        }

        BizEvent bizEvent = bizEventList.get(0);
        String transactionId = bizEvent.getTransactionDetails().getTransaction().getTransactionId();
        BigDecimal amount = getCartAmount(bizEvent);
        return CartForReceipt.builder()
                .id(transactionId)
                .eventId(transactionId)
                .version("1") // this is the first version of this document
                .payload(Payload.builder()
                        .payerFiscalCode(tokenizerPayerFiscalCode(bizEvent))
                        .totalNotice(Integer.parseInt(bizEvent.getPaymentInfo().getTotalNotice()))
                        .totalAmount(!amount.equals(BigDecimal.ZERO) ? formatAmount(amount.toString()) : null)
                        .transactionCreationDate(getTransactionCreationDate(bizEvent))
                        .cart(cartItems)
                        .build())
                .build();
    }

    @Override
    public List<BizEvent> getCartBizEvents(CartForReceipt cart) {
        List<BizEvent> bizEventList = new ArrayList<>();
        try {
            for (CartPayment item : cart.getPayload().getCart()) {
                BizEvent bizEvent = this.bizEventCosmosClient.getBizEventDocument(item.getBizEventId());
                bizEventList.add(bizEvent);
            }
        } catch (BizEventNotFoundException e) {
            String errMsg = String.format("Error while fetching cart with event id %s biz-events: %s", cart.getEventId(), e.getMessage());
            logger.error("{}", errMsg, e);
            cart.setStatus(CartStatusType.FAILED);
            cart.setReasonErr(ReasonError.builder()
                    .code(ReasonErrorCode.GENERIC_ERROR.getCode())
                    .message(errMsg)
                    .build());
        }
        return bizEventList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CartForReceipt saveCartForReceipt(CartForReceipt cartForReceipt, BizEvent bizEvent) {
        int statusCode;

        statusCode = trySaveCart(cartForReceipt);

        if (statusCode == ReasonErrorCode.ERROR_COSMOS_ETAG_MISMATCH.getCode()) {
            logger.debug("Fetch again cart with eventId {} and then retry save on cosmos", cartForReceipt.getEventId());
            cartForReceipt = buildCartForReceipt(bizEvent);

            if (!isCartStatusValid(cartForReceipt)) {
                logger.error("Cart build after fetch failed");
                return cartForReceipt;
            }
            statusCode = trySaveCart(cartForReceipt);
        }

        handleSaveCartFailure(cartForReceipt, statusCode);
        return cartForReceipt;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CartForReceipt saveCartForReceiptWithoutRetry(CartForReceipt cartForReceipt) {
        int statusCode;

        statusCode = trySaveCart(cartForReceipt);
        handleSaveCartFailure(cartForReceipt, statusCode);

        return cartForReceipt;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CartForReceipt getCartForReceipt(String cartId) throws CartNotFoundException {
        CartForReceipt cart;
        try {
            cart = this.cartReceiptsCosmosClient.getCartItem(cartId);
        } catch (CartNotFoundException e) {
            String errorMsg = String.format("CartForReceipt not found with the event id %s", cartId);
            throw new CartNotFoundException(errorMsg, e);
        }

        if (cart == null) {
            String errorMsg = String.format("CartForReceipt retrieved with the event id %s is null", cartId);
            throw new CartNotFoundException(errorMsg);
        }
        return cart;
    }

    private int trySaveCart(CartForReceipt cartForReceipt) {
        int statusCode;
        try {
            CosmosItemResponse<CartForReceipt> response = this.cartReceiptsCosmosClient.updateCart(cartForReceipt);

            statusCode = response.getStatusCode();
        } catch (CartConcurrentUpdateException e) {
            logger.error("Save cart with eventId {} on cosmos failed for concurrent update", cartForReceipt.getEventId(), e);
            statusCode = ReasonErrorCode.ERROR_COSMOS_ETAG_MISMATCH.getCode();
        } catch (Exception e) {
            statusCode = ReasonErrorCode.ERROR_COSMOS.getCode();
            logger.error("Save cart with eventId {} on cosmos failed", cartForReceipt.getEventId(), e);
        }
        return statusCode;
    }

    private void handleSaveCartFailure(CartForReceipt cartForReceipt, int statusCode) {
        if (statusCode != HttpStatus.CREATED.value() && statusCode != HttpStatus.OK.value()) {
            String errorString = String.format(
                    "[BizEventToReceiptService] Error saving cart to cosmos for receipt with eventId %s, cosmos client responded with status %s",
                    cartForReceipt.getEventId(), statusCode);
            ReasonError reasonError = new ReasonError(statusCode, errorString);
            cartForReceipt.setReasonErr(reasonError);
            cartForReceipt.setStatus(CartStatusType.FAILED);
            //Error info
            logger.error(errorString);
        }
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

    /**
     * @param cartForReceipt the cart to update with failed status
     * @param code           the error code
     * @param message        the error message
     * @return the cart with failed status
     */
    private CartForReceipt buildCartWithFailedStatus(String transactionId, CartForReceipt cartForReceipt, int code, String message, Exception e) {
        if (cartForReceipt == null) {
            cartForReceipt = new CartForReceipt();
        }
        logger.error("an error occurred during buildCartForReceipt method", e);
        return cartForReceipt.toBuilder()
                .status(CartStatusType.FAILED)
                .eventId(transactionId)
                .reasonErr(ReasonError.builder()
                        .code(code)
                        .message(message)
                        .build())
                .build();
    }

    private CartForReceipt buildCart(BizEvent bizEvent, String transactionId, List<CartPayment> cartItems) throws PDVTokenizerException, JsonProcessingException {
        BigDecimal amount = getCartAmount(bizEvent);

        return CartForReceipt.builder()
                // remove UUID suffix in order to grant document overwrite in case of concurrent insert, in this way
                // _etag check will avoid overwrite and prevent data loss
                .id(transactionId)
                .eventId(transactionId)
                .status(CartStatusType.WAITING_FOR_BIZ_EVENT)
                .version("1") // this is the first version of this document
                .payload(Payload.builder()
                        .payerFiscalCode(tokenizerPayerFiscalCode(bizEvent))
                        .totalNotice(Integer.parseInt(bizEvent.getPaymentInfo().getTotalNotice()))
                        .totalAmount(!amount.equals(BigDecimal.ZERO) ? formatAmount(amount.toString()) : null)
                        .transactionCreationDate(getTransactionCreationDate(bizEvent))
                        .cart(cartItems)
                        .build())
                // added custom initial _etag value in order to avoid document overwrite due to concurrent insert
                ._etag("cart-insert")
                .build();
    }

    private CartPayment buildCartPayment(BizEvent bizEvent) throws PDVTokenizerException, JsonProcessingException {
        String debtorFiscalCode = tokenizerDebtorFiscalCode(bizEvent);
        return CartPayment.builder()
                .bizEventId(bizEvent.getId())
                .amount(formatAmount(bizEvent.getPaymentInfo().getAmount()))
                .debtorFiscalCode(debtorFiscalCode)
                .payeeName(bizEvent.getCreditor() != null ? bizEvent.getCreditor().getCompanyName() : null)
                .subject(getItemSubject(bizEvent))
                .build();
    }

    private String tokenizerDebtorFiscalCode(BizEvent bizEvent) throws PDVTokenizerException, JsonProcessingException {
        return bizEvent.getDebtor() != null && isValidFiscalCode(bizEvent.getDebtor().getEntityUniqueIdentifierValue()) ?
                pdvTokenizerService.generateTokenForFiscalCodeWithRetry(bizEvent.getDebtor().getEntityUniqueIdentifierValue()) :
                FISCAL_CODE_ANONYMOUS;
    }


    /**
     * Find cart by transaction id in CosmosDB
     *
     * @param transactionId the transaction id
     * @return the cart if found, null otherwise
     */
    private CartForReceipt findCart(String transactionId) {
        try {
            return cartReceiptsCosmosClient.getCartItem(transactionId);
        } catch (CartNotFoundException e) {
            return null;
        }
    }

    private String tokenizerPayerFiscalCode(BizEvent bizEvent) throws PDVTokenizerException, JsonProcessingException {
        //Tokenize Payer
        if (isValidChannelOrigin(bizEvent)) {
            if (bizEvent.getTransactionDetails() != null &&
                    bizEvent.getTransactionDetails().getUser() != null &&
                    BizEventToReceiptUtils.isValidFiscalCode(bizEvent.getTransactionDetails().getUser().getFiscalCode())
            ) {
                return
                        pdvTokenizerService.generateTokenForFiscalCodeWithRetry(
                                bizEvent.getTransactionDetails().getUser().getFiscalCode());
            } else if (bizEvent.getPayer() != null && BizEventToReceiptUtils.isValidFiscalCode(bizEvent.getPayer().getEntityUniqueIdentifierValue())) {
                return
                        pdvTokenizerService.generateTokenForFiscalCodeWithRetry(bizEvent.getPayer().getEntityUniqueIdentifierValue())
                        ;
            }
        }
        return null;
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
}
