package it.gov.pagopa.receipt.pdf.datastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.CosmosDBTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.BizEventToReceiptServiceImpl;
import it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils.isReceiptStatusValid;

/**
 * Azure Functions with Azure CosmosDB trigger.
 */
public class CartEventToReceipt {

    private final Logger logger = LoggerFactory.getLogger(CartEventToReceipt.class);

    private final BizEventToReceiptService bizEventToReceiptService;

    public CartEventToReceipt() {
        this.bizEventToReceiptService = new BizEventToReceiptServiceImpl();
    }

    CartEventToReceipt(BizEventToReceiptService bizEventToReceiptService) {
        this.bizEventToReceiptService = bizEventToReceiptService;
    }

    /**
     * This function will be invoked when a CosmosDB trigger occurs on cart-for-receipts collection
     * <p>
     * It checks if all biz-event that compose the cart are collected and if so it proceeds with the following steps:
     * <ol>
     *     <li>Retrieve all biz events
     *     <li>Create the receipt ({@link Receipt})
     *     <li>Save the receipt in receipts collection on CosmosDB
     *     <li>Send the list of biz events in queue
     * </ol>
     * <p>
     * Otherwise, it skips the event.
     */
    @FunctionName("CartEventToReceiptProcessor")
    public void run(
            @CosmosDBTrigger(
                    name = "CartReceiptDatastore",
                    databaseName = "db",
                    containerName = "cart-for-receipts",
                    leaseContainerName = "cart-for-receipts-leases",
                    connection = "COSMOS_RECEIPTS_CONN_STRING",
                    createLeaseContainerIfNotExists = true)
            List<CartForReceipt> listCartForReceipt,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    containerName = "receipts",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<Receipt>> receiptDocumentdb,
            @CosmosDBOutput(
                    name = "CartReceiptDatastoreOutput",
                    databaseName = "db",
                    containerName = "cart-for-receipts",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<CartForReceipt>> cartForReceiptDocumentdb,
            final ExecutionContext context
    ) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());
        List<CartForReceipt> listOfCartEvents = new ArrayList<>();
        List<Receipt> listOfFailedReceipt = new ArrayList<>();

        for (CartForReceipt cartForReceipt : listCartForReceipt) {

            if (cartForReceipt.getTotalNotice() != cartForReceipt.getCartPaymentId().size() || !cartForReceipt.getStatus().equals(CartStatusType.INSERTED)) {
                String logMsg = !cartForReceipt.getStatus().equals(CartStatusType.INSERTED) ?
                        "[{}] Not all items collected for cart with id {}, this event will be skipped" :
                        "[{}] Cart with id {} not in status INSERTED, this event will be skipped";
                logger.info(logMsg, context.getFunctionName(), cartForReceipt.getId());
                continue;
            }
            List<BizEvent> bizEventList;
            Receipt receipt;
            try {
                bizEventList = this.bizEventToReceiptService.getCartBizEvents(cartForReceipt.getId());
                receipt = BizEventToReceiptUtils.createCartReceipt(bizEventList, bizEventToReceiptService, logger);

                if (isReceiptStatusValid(receipt)) {
                    // Add receipt to items to be saved on CosmosDB
                    this.bizEventToReceiptService.handleSaveReceipt(receipt);

                    if (isReceiptStatusValid(receipt)) {
                        // Send biz event as message to queue (to be processed from the other function)
                        this.bizEventToReceiptService.handleSendMessageToQueue(bizEventList, receipt);

                        if (!isReceiptStatusValid(receipt)) {
                            // save only if receipt save on cosmos or send on queue fail
                            listOfFailedReceipt.add(receipt);
                        }
                        cartForReceipt.setStatus(CartStatusType.SENT);
                        logger.debug("[{}] Cart with id {} processes successfully. Cart with status: {} and receipt with status: {}",
                                context.getFunctionName(), cartForReceipt.getId(), cartForReceipt.getStatus(), receipt.getStatus());
                    } else {
                        logger.error("[{}] Failed to process cart with id {}: fail to save receipt",
                                context.getFunctionName(), cartForReceipt.getId());
                        cartForReceipt.setStatus(CartStatusType.FAILED);
                        cartForReceipt.setReasonError(receipt.getReasonErr());
                    }
                } else {
                    logger.error("[{}] Failed to process cart with id {}: fail to tokenize fiscal codes",
                            context.getFunctionName(), cartForReceipt.getId());
                    cartForReceipt.setStatus(CartStatusType.FAILED);
                    cartForReceipt.setReasonError(receipt.getReasonErr());
                }
            } catch (Exception e) {
                logger.error("[{}] Failed to process cart with id {}: unexpected error",
                        context.getFunctionName(), cartForReceipt.getId(), e);
                cartForReceipt.setStatus(CartStatusType.FAILED);
                cartForReceipt.setReasonError(ReasonError.builder().code(HttpStatus.SC_INTERNAL_SERVER_ERROR).message(e.getMessage()).build());
            }

            listOfCartEvents.add(cartForReceipt);
        }

        if (!listOfCartEvents.isEmpty()) {
            cartForReceiptDocumentdb.setValue(listOfCartEvents);
        }

        if (!listOfFailedReceipt.isEmpty()) {
            receiptDocumentdb.setValue(listOfFailedReceipt);
        }
    }
}
