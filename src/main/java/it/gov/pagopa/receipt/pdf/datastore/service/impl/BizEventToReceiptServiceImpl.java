package it.gov.pagopa.receipt.pdf.datastore.service.impl;

import com.azure.core.http.rest.Response;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.storage.queue.models.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.receipt.pdf.datastore.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptQueueClient;
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
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.datastore.service.PDVTokenizerServiceRetryWrapper;
import it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils;
import it.gov.pagopa.receipt.pdf.datastore.utils.ObjectMapperUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class BizEventToReceiptServiceImpl implements BizEventToReceiptService {

    private final Logger logger = LoggerFactory.getLogger(BizEventToReceiptServiceImpl.class);

    private final PDVTokenizerServiceRetryWrapper pdvTokenizerService;
    private final ReceiptCosmosClient receiptCosmosClient;

    private final CartReceiptsCosmosClient cartReceiptsCosmosClient;

    private final ReceiptQueueClient queueClient;

    public BizEventToReceiptServiceImpl() {
        this.pdvTokenizerService = new PDVTokenizerServiceRetryWrapperImpl();
        this.receiptCosmosClient = ReceiptCosmosClientImpl.getInstance();
        this.cartReceiptsCosmosClient = CartReceiptsCosmosClientImpl.getInstance();
        this.queueClient = ReceiptQueueClientImpl.getInstance();
    }

    public BizEventToReceiptServiceImpl(PDVTokenizerServiceRetryWrapper pdvTokenizerService,
                                        ReceiptCosmosClient receiptCosmosClient,
                                        CartReceiptsCosmosClientImpl cartReceiptsCosmosClient,
                                        ReceiptQueueClient queueClient) {
        this.pdvTokenizerService = pdvTokenizerService;
        this.receiptCosmosClient = receiptCosmosClient;
        this.queueClient = queueClient;
        this.cartReceiptsCosmosClient = cartReceiptsCosmosClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleSendMessageToQueue(BizEvent bizEvent, Receipt receipt) {
        //Encode biz-event to base64 string
        String messageText = Base64.getMimeEncoder().encodeToString(
                Objects.requireNonNull(ObjectMapperUtils.writeValueAsString(bizEvent)).getBytes(StandardCharsets.UTF_8)
        );

        //Add message to the queue
        int statusCode;
        try {
            Response<SendMessageResult> sendMessageResult = queueClient.sendMessageToQueue(messageText);

            statusCode = sendMessageResult.getStatusCode();
        } catch (Exception e) {
            statusCode = ReasonErrorCode.ERROR_QUEUE.getCode();
            logger.error(String.format("Sending BizEvent with id %s to queue failed", bizEvent.getId()), e);
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
    public void handleSaveReceipt(Receipt receipt) {
        int statusCode;

        try {
            receipt.setStatus(ReceiptStatusType.INSERTED);
            receipt.setInserted_at(System.currentTimeMillis());
            CosmosItemResponse<Receipt> response = receiptCosmosClient.saveReceipts(receipt);

            statusCode = response.getStatusCode();
        } catch (Exception e) {
            statusCode = ReasonErrorCode.ERROR_COSMOS.getCode();
            logger.error(String.format("Save receipt with eventId %s on cosmos failed", receipt.getEventId()), e);
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
            if (bizEvent.getDebtor() != null && bizEvent.getDebtor().getEntityUniqueIdentifierValue() != null) {
                eventData.setDebtorFiscalCode(
                        pdvTokenizerService.generateTokenForFiscalCodeWithRetry(bizEvent.getDebtor().getEntityUniqueIdentifierValue())
                );
            }
            if (bizEvent.getPayer() != null && bizEvent.getPayer().getEntityUniqueIdentifierValue() != null) {
                eventData.setPayerFiscalCode(
                        pdvTokenizerService.generateTokenForFiscalCodeWithRetry(bizEvent.getPayer().getEntityUniqueIdentifierValue())
                );
            }
        } catch (PDVTokenizerException e) {
            handleTokenizerException(receipt, e.getMessage(), e.getStatusCode());
            throw e;
        } catch (JsonProcessingException e) {
            handleTokenizerException(receipt, e.getMessage(), ReasonErrorCode.ERROR_PDV_MAPPING.getCode());
            throw e;
        }
    }

    @Override
    public void handleSaveCart(BizEvent bizEvent) {
        long transactionId = bizEvent.getTransactionDetails().getTransaction().getIdTransaction();
        CartForReceipt cartForReceipt;
        try {
            cartForReceipt = cartReceiptsCosmosClient.getCartItem(String.valueOf(transactionId));
            if (cartForReceipt == null) {
                throw new CartNotFoundException("Missing Cart");
            }
        } catch (CartNotFoundException ignore) {
            cartForReceipt = CartForReceipt.builder().id(transactionId).status(CartStatusType.INSERTED).cartPaymentId(new HashSet<>())
                    .totalNotice(BizEventToReceiptUtils.getTotalNotice(bizEvent, null, null)).build();
        }
        cartForReceipt.getCartPaymentId().add(bizEvent.getId());
        cartReceiptsCosmosClient.saveCart(cartForReceipt);
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
