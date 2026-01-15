package it.gov.pagopa.receipt.pdf.datastore.service.impl;

import com.azure.cosmos.models.FeedResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import it.gov.pagopa.receipt.pdf.datastore.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventBadRequestException;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventUnprocessableEntityException;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.model.MassiveCartRecoverResult;
import it.gov.pagopa.receipt.pdf.datastore.model.MassiveRecoverResult;
import it.gov.pagopa.receipt.pdf.datastore.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.datastore.service.CartReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.datastore.service.HelpdeskService;
import it.gov.pagopa.receipt.pdf.datastore.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils.createReceipt;
import static it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils.isCartStatusValid;
import static it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils.isReceiptStatusValid;

public class HelpdeskServiceImpl implements HelpdeskService {

    private final Logger logger = LoggerFactory.getLogger(HelpdeskServiceImpl.class);

    private final ReceiptCosmosService receiptCosmosService;
    private final CartReceiptCosmosService cartReceiptCosmosService;
    private final BizEventToReceiptService bizEventToReceiptService;
    private final BizEventCosmosClient bizEventCosmosClient;

    public HelpdeskServiceImpl() {
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
        this.cartReceiptCosmosService = new CartReceiptCosmosServiceImpl();
        this.bizEventToReceiptService = new BizEventToReceiptServiceImpl();
        this.bizEventCosmosClient = BizEventCosmosClientImpl.getInstance();
    }

    public HelpdeskServiceImpl(
            ReceiptCosmosService receiptCosmosService, CartReceiptCosmosService cartReceiptCosmosService,
            BizEventToReceiptService bizEventToReceiptService,
            BizEventCosmosClient bizEventCosmosClient
    ) {
        this.receiptCosmosService = receiptCosmosService;
        this.cartReceiptCosmosService = cartReceiptCosmosService;
        this.bizEventToReceiptService = bizEventToReceiptService;
        this.bizEventCosmosClient = bizEventCosmosClient;
    }

    @Override
    public Receipt recoverFailedReceipt(Receipt existingReceipt)
            throws BizEventUnprocessableEntityException, BizEventBadRequestException, BizEventNotFoundException {
        // retrieve biz-event with the specified cartId

        BizEvent bizEvent = this.bizEventCosmosClient.getBizEventDocument(existingReceipt.getEventId());
        validateBizEvent(bizEvent, 1);

        Receipt receipt = createReceipt(bizEvent, bizEventToReceiptService, logger);

        if (isReceiptStatusValid(receipt)) {
            this.bizEventToReceiptService.handleSaveReceipt(receipt);
        }

        if (isReceiptStatusValid(receipt)) {
            this.bizEventToReceiptService.handleSendMessageToQueue(Collections.singletonList(bizEvent), receipt);
        }
        return receipt;
    }

    @Override
    public CartForReceipt recoverFailedCart(CartForReceipt existingCart)
            throws BizEventUnprocessableEntityException, BizEventBadRequestException, PDVTokenizerException, JsonProcessingException {
        // retrieve biz-event with the specified cartId
        List<BizEvent> bizEvents = this.bizEventCosmosClient.getAllCartBizEventDocument(existingCart.getEventId());
        validateCartBizEvents(bizEvents);

        CartForReceipt cart = this.bizEventToReceiptService.buildCartFromBizEventList(bizEvents);

        if (isCartStatusValid(cart)) {
            // set _etag field with existing cart value in order to avoid error on document overwrite
            cart.set_etag(existingCart.get_etag());
            cart = this.bizEventToReceiptService.saveCartForReceiptWithoutRetry(cart);
        }

        if (isCartStatusValid(cart)) {
            this.bizEventToReceiptService.handleSendCartMessageToQueue(bizEvents, cart);
        }
        return cart;
    }

    @Override
    public Receipt recoverNoNotifiedReceipt(Receipt receipt) {
        receipt.setStatus(ReceiptStatusType.GENERATED);
        receipt.setNotificationNumRetry(0);
        receipt.setNotified_at(0);

        receipt.setReasonErr(null);
        receipt.setReasonErrPayer(null);

        return receipt;
    }

    @Override
    public CartForReceipt recoverNoNotifiedCart(CartForReceipt cart) {
        cart.setStatus(CartStatusType.GENERATED);
        cart.setNotificationNumRetry(0);
        cart.setNotified_at(0);

        cart.setReasonErr(null);
        if (cart.getPayload() != null
                && cart.getPayload().getCart() != null
        ) {
            cart.getPayload().getCart().forEach(cartPayment -> cartPayment.setReasonErrDebtor(null));
        }

        return cart;
    }

    @Override
    public MassiveRecoverResult massiveRecoverByStatus(ReceiptStatusType status) {
        List<Receipt> failedReceipts = new ArrayList<>();
        int successCounter = 0;
        int errorCounter = 0;
        String continuationToken = null;
        do {
            Iterable<FeedResponse<Receipt>> feedResponseIterator =
                    this.receiptCosmosService.getFailedReceiptByStatus(continuationToken, 100, status);

            for (FeedResponse<Receipt> page : feedResponseIterator) {
                for (Receipt receipt : page.getResults()) {
                    try {
                        Receipt restored = recoverFailedReceipt(receipt);

                        if (isReceiptStatusValid(restored)) {
                            successCounter++;
                        } else {
                            failedReceipts.add(restored);
                            errorCounter++;
                        }
                    } catch (Exception e) {
                        logger.warn("Recover for receipt {} failed", receipt.getEventId(), e);
                        errorCounter++;
                    }
                }
                continuationToken = page.getContinuationToken();
            }
        } while (continuationToken != null);

        return MassiveRecoverResult.builder()
                .failedReceiptList(failedReceipts)
                .successCounter(successCounter)
                .errorCounter(errorCounter)
                .build();
    }

    @Override
    public MassiveCartRecoverResult massiveRecoverByStatus(CartStatusType status) {
        List<CartForReceipt> failedCart = new ArrayList<>();
        int successCounter = 0;
        int errorCounter = 0;
        String continuationToken = null;
        do {
            Iterable<FeedResponse<CartForReceipt>> feedResponseIterator =
                    this.cartReceiptCosmosService.getFailedCartReceiptByStatus(continuationToken, 100, status);

            for (FeedResponse<CartForReceipt> page : feedResponseIterator) {
                for (CartForReceipt cart : page.getResults()) {
                    try {
                        CartForReceipt recoverCart = recoverFailedCart(cart);

                        if (isCartStatusValid(recoverCart)) {
                            successCounter++;
                        } else {
                            failedCart.add(recoverCart);
                            errorCounter++;
                        }
                    } catch (Exception e) {
                        logger.warn("Recover for cart {} failed", cart.getEventId(), e);
                        errorCounter++;
                    }
                }
                continuationToken = page.getContinuationToken();
            }
        } while (continuationToken != null);

        return MassiveCartRecoverResult.builder()
                .failedCartList(failedCart)
                .successCounter(successCounter)
                .errorCounter(errorCounter)
                .build();
    }

    @Override
    public List<Receipt> massiveRecoverNoNotified(ReceiptStatusType status) {
        List<Receipt> receiptList = new ArrayList<>();
        String continuationToken = null;
        do {
            Iterable<FeedResponse<Receipt>> feedResponseIterator =
                    this.receiptCosmosService.getNotNotifiedReceiptByStatus(continuationToken, 100, status);

            for (FeedResponse<Receipt> page : feedResponseIterator) {
                for (Receipt receipt : page.getResults()) {
                    Receipt restoredReceipt = recoverNoNotifiedReceipt(receipt);
                    receiptList.add(restoredReceipt);
                }
                continuationToken = page.getContinuationToken();

            }
        } while (continuationToken != null);

        return receiptList;
    }

    @Override
    public List<CartForReceipt> massiveRecoverNoNotified(CartStatusType status) {
        List<CartForReceipt> carttList = new ArrayList<>();
        String continuationToken = null;
        do {
            Iterable<FeedResponse<CartForReceipt>> feedResponseIterator =
                    this.cartReceiptCosmosService.getNotNotifiedCartReceiptByStatus(continuationToken, 100, status);

            for (FeedResponse<CartForReceipt> page : feedResponseIterator) {
                for (CartForReceipt cart : page.getResults()) {
                    CartForReceipt restoredReceipt = recoverNoNotifiedCart(cart);
                    carttList.add(restoredReceipt);
                }
                continuationToken = page.getContinuationToken();

            }
        } while (continuationToken != null);

        return carttList;
    }

    private void validateCartBizEvents(List<BizEvent> bizEvents) throws BizEventBadRequestException, BizEventUnprocessableEntityException {
        if (bizEvents.isEmpty()) {
            throw new BizEventBadRequestException("BizEvents for the specified cart not found");
        }

        for (BizEvent bizEvent : bizEvents) {
            validateBizEvent(bizEvent, bizEvents.size());
        }
    }

    private void validateBizEvent(
            BizEvent bizEvent, int expectedTotalNotice
    ) throws BizEventBadRequestException, BizEventUnprocessableEntityException {
        BizEventToReceiptUtils.BizEventValidityCheck bizEventValidityCheck = BizEventToReceiptUtils.isBizEventInvalid(bizEvent);
        if (bizEventValidityCheck.invalid()) {
            String errMsg = String.format("Biz-event with id %s is invalid: %s", bizEvent.getId(), bizEventValidityCheck.error());
            throw new BizEventBadRequestException(errMsg);
        }

        // total notice check
        Integer totalNotice = BizEventToReceiptUtils.getTotalNotice(bizEvent, logger);
        if (totalNotice != expectedTotalNotice) {
            String errMsg = String.format("The expected total notice %s does not match the number of biz events %s",
                    totalNotice, expectedTotalNotice);
            throw new BizEventUnprocessableEntityException(errMsg);
        }
    }
}
