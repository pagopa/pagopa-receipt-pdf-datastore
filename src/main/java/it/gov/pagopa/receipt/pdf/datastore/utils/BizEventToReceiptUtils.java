package it.gov.pagopa.receipt.pdf.datastore.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.CartItem;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.service.PDVTokenizerService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.BizEventToReceiptServiceImpl;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.PDVTokenizerServiceImpl;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.List;

public class BizEventToReceiptUtils {

    public static Receipt createReceipt(BizEvent bizEvent, BizEventToReceiptServiceImpl service,
                                         PDVTokenizerService pdvTokenizerService)
            throws PDVTokenizerException, JsonProcessingException {
        Receipt receipt = new Receipt();

        // Insert biz-event data into receipt
        receipt.setEventId(bizEvent.getId());

        EventData eventData = new EventData();
        if(bizEvent.getPayer() != null && bizEvent.getPayer().getEntityUniqueIdentifierValue() != null){
            eventData.setPayerFiscalCode(
                    pdvTokenizerService.generateTokenForFiscalCode(bizEvent.getPayer().getEntityUniqueIdentifierValue())
            );
        }
        if(bizEvent.getDebtor() != null && bizEvent.getDebtor().getEntityUniqueIdentifierValue() != null){
            eventData.setDebtorFiscalCode(
                    pdvTokenizerService.generateTokenForFiscalCode(bizEvent.getDebtor().getEntityUniqueIdentifierValue())
            );
        }
        eventData.setTransactionCreationDate(
                service.getTransactionCreationDate(bizEvent));
        eventData.setAmount( bizEvent.getPaymentInfo() != null ?
                bizEvent.getPaymentInfo().getAmount() : null);

        CartItem item = new CartItem();
        item.setPayeeName(bizEvent.getCreditor() != null ? bizEvent.getCreditor().getOfficeName() : null);
        item.setSubject(bizEvent.getPaymentInfo() != null ? bizEvent.getPaymentInfo().getRemittanceInformation() : null);
        List<CartItem> cartItems = Collections.singletonList(item);
        eventData.setCart(cartItems);

        receipt.setEventData(eventData);
        return receipt;
    }

    public static boolean isBizEventInvalid(BizEvent bizEvent, ExecutionContext context, Logger logger) {

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

}
