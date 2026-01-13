package it.gov.pagopa.receipt.pdf.datastore.service.impl;

import com.azure.cosmos.models.FeedResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import it.gov.pagopa.receipt.pdf.datastore.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventBadRequestException;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventUnprocessableEntityException;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.model.MassiveCartRecoverResult;
import it.gov.pagopa.receipt.pdf.datastore.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.datastore.service.HelpdeskService;
import it.gov.pagopa.receipt.pdf.datastore.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils.isCartStatusValid;

public class HelpdeskServiceImpl implements HelpdeskService {

    private final Logger logger = LoggerFactory.getLogger(HelpdeskServiceImpl.class);

    private final ReceiptCosmosService receiptCosmosService;
    private final BizEventToReceiptService bizEventToReceiptService;
    private final BizEventCosmosClient bizEventCosmosClient;

    public HelpdeskServiceImpl() {
        this.bizEventToReceiptService = new BizEventToReceiptServiceImpl();
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
        this.bizEventCosmosClient = BizEventCosmosClientImpl.getInstance();
    }

    public HelpdeskServiceImpl(
            ReceiptCosmosService receiptCosmosService,
            BizEventToReceiptService bizEventToReceiptService,
            BizEventCosmosClient bizEventCosmosClient
    ) {
        this.receiptCosmosService = receiptCosmosService;
        this.bizEventToReceiptService = bizEventToReceiptService;
        this.bizEventCosmosClient = bizEventCosmosClient;
    }

    @Override
    public CartForReceipt recoverCart(CartForReceipt existingCart)
            throws BizEventUnprocessableEntityException, BizEventBadRequestException, PDVTokenizerException, JsonProcessingException {
        // retrieve biz-event with the specified cartId
        List<BizEvent> bizEvents = this.bizEventCosmosClient.getAllCartBizEventDocument(existingCart.getEventId());
        validateCartBizEvents(bizEvents);

        CartForReceipt cart = this.bizEventToReceiptService.buildCartFromBizEventList(bizEvents);
        cart.set_etag(existingCart.get_etag());

        if (isCartStatusValid(cart)) {
            cart = this.bizEventToReceiptService.saveCartForReceiptWithoutRetry(cart);
        }

        if (isCartStatusValid(cart)) {
            this.bizEventToReceiptService.handleSendCartMessageToQueue(bizEvents, cart);
        }
        return cart;
    }

    @Override
    public MassiveCartRecoverResult massiveRecoverByStatus(CartStatusType status) {
        List<CartForReceipt> failedCart = new ArrayList<>();
        int successCounter = 0;
        int errorCounter = 0;
        String continuationToken = null;
        do {
            Iterable<FeedResponse<CartForReceipt>> feedResponseIterator =
                    this.receiptCosmosService.getFailedCartReceiptByStatus(continuationToken, 100, status);

            for (FeedResponse<CartForReceipt> page : feedResponseIterator) {
                for (CartForReceipt cart : page.getResults()) {
                    try {
                        CartForReceipt recoverCart = recoverCart(cart);

                        if (!isCartStatusValid(recoverCart)) {
                            failedCart.add(recoverCart);
                            errorCounter++;
                        } else {
                            successCounter++;
                        }
                    } catch (Exception e) {
                        logger.error("Recover for cart {} failed", cart.getEventId(), e);
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

    private void validateCartBizEvents(List<BizEvent> bizEvents) throws BizEventBadRequestException, BizEventUnprocessableEntityException {
        if (bizEvents.isEmpty()) {
            String errMsg = "BizEvents for the specified cart not found";
            logger.error(errMsg);
            throw new BizEventBadRequestException(errMsg);
        }

        for (BizEvent bizEvent : bizEvents) {
            // biz-event validation
            BizEventToReceiptUtils.BizEventValidityCheck bizEventValidityCheck = BizEventToReceiptUtils.isBizEventInvalid(bizEvent);
            if (bizEventValidityCheck.invalid()) {
                String errMsg = String.format("Biz-event with id %s is invalid: %s", bizEvent.getId(), bizEventValidityCheck.error());
                throw new BizEventBadRequestException(errMsg);
            }

            // total notice check
            Integer totalNotice = BizEventToReceiptUtils.getTotalNotice(bizEvent, logger);
            if (totalNotice != bizEvents.size()) {
                String errMsg = String.format("The expected total notice %s does not match the number of biz events %s",
                        totalNotice, bizEvents.size());
                logger.error(errMsg);
                throw new BizEventUnprocessableEntityException(errMsg);
            }
        }
    }
}
