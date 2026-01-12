package it.gov.pagopa.receipt.pdf.datastore.service.impl;

import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventBadRequestException;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventUnprocessableEntityException;
import it.gov.pagopa.receipt.pdf.datastore.service.HelpdeskService;
import it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class HelpdeskServiceImpl implements HelpdeskService {

    private final Logger logger = LoggerFactory.getLogger(HelpdeskServiceImpl.class);

    @Override
    public void validateCartBizEvents(List<BizEvent> bizEvents) throws BizEventBadRequestException, BizEventUnprocessableEntityException {
        if (bizEvents.isEmpty()) {
            String errMsg = String.format("BizEvents for cart %s not found", cartId);
            logger.error(errMsg);
            throw new BizEventBadRequestException(errMsg);
        }

        for(BizEvent bizEvent : bizEvents) {
            // biz-event validation
            if (BizEventToReceiptUtils.isBizEventInvalid(bizEvent, context, logger)) {
                String errMsg = String.format("Biz-event with id %s is invalid", bizEvent.getId());
                throw new BizEventBadRequestException(errMsg);
            }

            // total notice check
            Integer totalNotice = BizEventToReceiptUtils.getTotalNotice(bizEvent, context, logger);
            if (totalNotice != bizEvents.size()) {
                String errMsg = String.format("Failed to regenerate cart, the expected total notice %s does not match the number of biz events %s",
                        totalNotice, bizEvents.size());
                logger.error(errMsg);
                throw new BizEventUnprocessableEntityException(errMsg);
            }
        }
    }
}
