package it.gov.pagopa.receipt.pdf.datastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.CosmosDBTrigger;
import com.microsoft.azure.functions.annotation.ExponentialBackoffRetry;
import com.microsoft.azure.functions.annotation.FunctionName;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.BizEventToReceiptServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils.createReceipt;
import static it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils.getTotalNotice;
import static it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils.isBizEventInvalid;
import static it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils.isCartStatusValid;
import static it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils.isReceiptStatusValid;

/**
 * Azure Functions with Azure CosmosDB trigger.
 */
public class BizEventToReceipt {

    private final Logger logger = LoggerFactory.getLogger(BizEventToReceipt.class);

    private final Boolean isCartEnabled = Boolean.parseBoolean(System.getenv().getOrDefault("ENABLE_CART", "false"));

    private final BizEventToReceiptService bizEventToReceiptService;

    public BizEventToReceipt() {
        this.bizEventToReceiptService = new BizEventToReceiptServiceImpl();
    }

    BizEventToReceipt(BizEventToReceiptService bizEventToReceiptService) {
        this.bizEventToReceiptService = bizEventToReceiptService;
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
                    containerName = "biz-events",
                    leaseContainerName = "biz-events-leases",
                    leaseContainerPrefix = "materialized",
                    createLeaseContainerIfNotExists = true,
                    maxItemsPerInvocation = 100,
                    connection = "COSMOS_BIZ_EVENT_CONN_STRING")
            List<BizEvent> items,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    containerName = "receipts",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<Receipt>> documentdb,
            @CosmosDBOutput(
                    name = "CartDatastore",
                    databaseName = "db",
                    containerName = "cart-for-receipts",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<CartForReceipt>> cartDocumentdb,
            final ExecutionContext context) {

        int itemsDone = 0;
        List<Receipt> receiptFailed = new ArrayList<>();
        List<CartForReceipt> cartFailed = new ArrayList<>();

        logger.info("[{}] stat {} function - num events triggered {}",
                context.getFunctionName(),
                context.getInvocationId(),
                items.size());
        int discarder = 0;

        // Retrieve receipt data from biz-event
        for (BizEvent bizEvent : items) {

            /*
            Discard biz-events:
              - null
              - not in status DONE
              - with invalid fiscal codes
              - eCommerce filter (if enabled)
              - legacy cart
              - already processed
             */
            if (isBizEventInvalid(bizEvent, context, logger) || isBizEventAlreadyProcessed(context, bizEvent)) {
                discarder++;
                continue;
            }

            logger.debug("[{}] function called at {} for event with id {} and status {}",
                    context.getFunctionName(), LocalDateTime.now(), bizEvent.getId(), bizEvent.getEventStatus());

            Integer totalNotice = getTotalNotice(bizEvent, context, logger);
            if (totalNotice == 1) {

                Receipt receipt = createReceipt(bizEvent, this.bizEventToReceiptService, logger);

                if (isReceiptStatusValid(receipt)) {
                    // Add receipt to items to be saved on CosmosDB
                    this.bizEventToReceiptService.handleSaveReceipt(receipt);
                }

                if (isReceiptStatusValid(receipt)) {
                    // Send biz event as message to queue (to be processed from the other function)
                    this.bizEventToReceiptService.handleSendMessageToQueue(bizEvent, receipt);
                }

                if (!isReceiptStatusValid(receipt)) {
                    receiptFailed.add(receipt);
                }
            } else if (Boolean.TRUE.equals(isCartEnabled) && totalNotice > 1) {

                CartForReceipt cartForReceipt = this.bizEventToReceiptService.buildCartForReceipt(bizEvent);

                if (isCartStatusValid(cartForReceipt)) {
                    // saved on CosmosDB
                    cartForReceipt = this.bizEventToReceiptService.saveCartForReceipt(cartForReceipt, bizEvent);
                }

                if (cartForReceipt.getStatus().equals(CartStatusType.INSERTED)) {
                    // Send biz event as message to queue (to be processed from the other function)
                    List<BizEvent> bizEvents = this.bizEventToReceiptService.getCartBizEvents(cartForReceipt);
                    if (isCartStatusValid(cartForReceipt)) {
                        this.bizEventToReceiptService.handleSendCartMessageToQueue(bizEvents, cartForReceipt);
                    }
                }

                if (!isCartStatusValid(cartForReceipt)) {
                    cartFailed.add(cartForReceipt);
                }
            }

            itemsDone++;
        }
        // Discarder info
        logger.debug("[{}] itemsDone stat {} function - {} number of events in discarder", context.getFunctionName(),
                context.getInvocationId(), discarder);
        // Call to queue info
        logger.debug("[{}] itemsDone stat {} function - number of events in DONE sent to the receipt queue {}",
                context.getFunctionName(), context.getInvocationId(), itemsDone);
        // Call to datastore info
        logger.debug("[{}] stat {} function - number of receipts inserted on the datastore {}",
                context.getFunctionName(),
                context.getInvocationId(), itemsDone);

        // Save failed receipts to CosmosDB
        if (!receiptFailed.isEmpty()) {
            // Call to datastore info
            logger.debug("[{}] stat {} function - number of receipts failed inserted on the datastore {}",
                    context.getFunctionName(),
                    context.getInvocationId(), receiptFailed.size());
            documentdb.setValue(receiptFailed);
        }        // Save failed receipts to CosmosDB
        if (!cartFailed.isEmpty()) {
            // Call to datastore info
            logger.debug("[{}] stat {} function - number of carts failed inserted on the datastore {}",
                    context.getFunctionName(),
                    context.getInvocationId(), receiptFailed.size());
            cartDocumentdb.setValue(cartFailed);
        }
    }

    private boolean isBizEventAlreadyProcessed(ExecutionContext context, BizEvent bizEvent) {
        Integer totalNotice = getTotalNotice(bizEvent, context, logger);
        if (totalNotice == 1) {
            try {
                Receipt receipt = this.bizEventToReceiptService.getReceipt(bizEvent.getId());
                logger.debug("[{}] event with id {} discarded because already processed, receipt already exist with id {}",
                        context.getFunctionName(), bizEvent.getId(), receipt.getId());
                return true;
            } catch (ReceiptNotFoundException ignored) {
                // the receipt does not exist
            }
        } else {
            try {
                String transactionId = bizEvent.getTransactionDetails().getTransaction().getTransactionId();
                CartForReceipt cart = this.bizEventToReceiptService.getCartForReceipt(transactionId);
                if (isBizEventInCart(cart, bizEvent.getId())
                ) {
                    logger.debug("[{}] event with id {} discarded because already processed, cart-for-receipts already exist with id {}",
                            context.getFunctionName(), bizEvent.getId(), transactionId);
                    return true;
                }
            } catch (CartNotFoundException ignored) {
                // the cart does not exist
            }
        }
        return false;
    }

    private boolean isBizEventInCart(CartForReceipt cart, String bizEventId) {
        return cart != null
                && cart.getPayload() != null
                && cart.getPayload().getCart() != null
                && cart.getPayload().getCart().stream().anyMatch(cartPayment -> cartPayment.getBizEventId().equals(bizEventId));
    }
}
