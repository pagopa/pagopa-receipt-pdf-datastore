package it.gov.pagopa.receipt.pdf.datastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.CosmosDBTrigger;
import com.microsoft.azure.functions.annotation.ExponentialBackoffRetry;
import com.microsoft.azure.functions.annotation.FunctionName;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.CartItem;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.BizEventToReceiptServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Azure Functions with Azure CosmosDB trigger.
 */
public class BizEventToReceipt {

    private final Logger logger = LoggerFactory.getLogger(BizEventToReceipt.class);
    private final BizEventToReceiptServiceImpl receiptService;

    public BizEventToReceipt() {
        this.receiptService = new BizEventToReceiptServiceImpl();
    }

    public BizEventToReceipt(BizEventToReceiptServiceImpl receiptService) {
        this.receiptService = receiptService;
    }

    /**
     * This function will be invoked when an CosmosDB trigger occurs
     * #
     * A receipt object is generated from the biz-event's data
     * The biz-event is encoded in a base64 string and sent as message to a queue
     * The receipt is saved to a specific Cosmos database with the following status:
     * - INSERTED if sending the message to the queue succeeded
     * - NOT_QUEUE_SENT if sending the message to the queue failed
     * #
     *
     * @param items      Biz-events that triggered the function from the Cosmos
     *                   database
     * @param documentdb Output binding that will save the receipt data retrieved
     *                   from the biz-events
     * @param context    Function context
     */
    @FunctionName("BizEventToReceiptProcessor")
    @ExponentialBackoffRetry(maxRetryCount = 5, minimumInterval = "500", maximumInterval = "5000")
    public void processBizEventToReceipt(
            @CosmosDBTrigger(
                    name = "BizEventDatastore",
                    databaseName = "db",
                    collectionName = "biz-events",
                    leaseCollectionName = "biz-events-leases",
                    leaseCollectionPrefix = "materialized",
                    createLeaseCollectionIfNotExists = true,
                    maxItemsPerInvocation = 100,
                    connectionStringSetting = "COSMOS_BIZ_EVENT_CONN_STRING")
            List<BizEvent> items,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    collectionName = "receipts",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<Receipt>> documentdb,
            final ExecutionContext context) {

        List<Receipt> itemsDone = new ArrayList<>();

        logger.info("[{}] stat {} function - num events triggered {}",
                context.getFunctionName(),
                context.getInvocationId(),
                items.size());
        int discarder = 0;

        // Retrieve receipt data from biz-event
        for (BizEvent bizEvent : items) {

            // Discard null biz events OR not in status DONE OR with totalNotice > 1
            if (isBizEventInvalid(bizEvent, context)) {
                discarder++;
                continue;
            }

            Receipt receipt = new Receipt();

            // Insert biz-event data into receipt
            receipt.setEventId(bizEvent.getId());

            EventData eventData = new EventData();

            try{
                receiptService.tokenizeFiscalCodes(bizEvent, receipt, eventData);
            } catch (Exception e){
                logger.error("Error tokenizing receipt with bizEventId {}", bizEvent.getId(), e);
                itemsDone.add(receipt);
                continue;
            }

            try {
                eventData.setTransactionCreationDate(
                        receiptService.getTransactionCreationDate(bizEvent));
                eventData.setAmount(bizEvent.getPaymentInfo() != null ?
                        bizEvent.getPaymentInfo().getAmount() : null);

                CartItem item = new CartItem();
                item.setPayeeName(bizEvent.getCreditor() != null ? bizEvent.getCreditor().getOfficeName() : null);
                item.setSubject(bizEvent.getPaymentInfo() != null ? bizEvent.getPaymentInfo().getRemittanceInformation() : null);
                List<CartItem> cartItems = Collections.singletonList(item);
                eventData.setCart(cartItems);

                receipt.setEventData(eventData);

                logger.info("[{}] function called at {} for event with id {} and status {}",
                        context.getFunctionName(), LocalDateTime.now(), bizEvent.getId(), bizEvent.getEventStatus());

                // Send biz event as message to queue (to be processed from the other function)
                receiptService.handleSendMessageToQueue(bizEvent, receipt);

                // Add receipt to items to be saved on CosmosDB
                itemsDone.add(receipt);
            } catch (Exception e) {
                discarder++;
                // Error info
                logger.error("[{}] Error to process event with id {}", context.getFunctionName(), bizEvent.getId(), e);
            }
        }
        // Discarder info
        logger.debug("[{}] itemsDone stat {} function - {} number of events in discarder", context.getFunctionName(),
                context.getInvocationId(), discarder);
        // Call to queue info
        logger.debug("[{}] itemsDone stat {} function - number of events in DONE sent to the receipt queue {}",
                context.getFunctionName(), context.getInvocationId(), itemsDone.size());
        // Call to datastore info
        logger.debug("[{}] stat {} function - number of receipts inserted on the datastore {}",
                context.getFunctionName(),
                context.getInvocationId(), itemsDone.size());

        // Save receipts data to CosmosDB
        if (!itemsDone.isEmpty()) {
            documentdb.setValue(itemsDone);
        }
    }

    private boolean isBizEventInvalid(BizEvent bizEvent, ExecutionContext context) {
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
