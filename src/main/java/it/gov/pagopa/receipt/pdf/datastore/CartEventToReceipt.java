package it.gov.pagopa.receipt.pdf.datastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.CosmosDBTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.BizEventToReceiptServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

import static it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils.isReceiptStatusValid;

/**
 * Azure Functions with Cosmos DB trigger.
 * <p>
 * This template uses an outdated version of the Azure Cosmos DB extension. Learn about the new extension at https://aka.ms/cosmos-db-azure-functions-extension-v4
 */
public class CartEventToReceipt {

    private final Logger logger = LoggerFactory.getLogger(CartEventToReceipt.class);

    private final BizEventToReceiptService bizEventToReceiptService;

    public CartEventToReceipt() {
        this.bizEventToReceiptService = new BizEventToReceiptServiceImpl();
    }

    /**
     * This function will be invoked when a CosmosDB trigger occurs on cart-for-receipt collection
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
                    collectionName = "cart-for-receipt",
                    leaseCollectionName = "cart-for-receipt-leases",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING",
                    createLeaseCollectionIfNotExists = true)
            CartForReceipt cartForReceipt,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    collectionName = "receipts",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<Receipt> documentdb,
            final ExecutionContext context
    ) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        if (cartForReceipt.getTotalNotice() != cartForReceipt.getCartPaymentId().size()) {
            logger.info("[{}] Not all items collected for cart with id {}, this event will be skipped",
                    context.getFunctionName(), cartForReceipt.getId());
            return;
        }

        List<BizEvent> bizEventList = this.bizEventToReceiptService.getCartBizEvents(cartForReceipt.getId());

        Receipt receipt = this.bizEventToReceiptService.createCartReceipt(bizEventList);

        if (isReceiptStatusValid(receipt)) {
            // Add receipt to items to be saved on CosmosDB
            this.bizEventToReceiptService.handleSaveReceipt(receipt);
        }

        if (isReceiptStatusValid(receipt)) {
            // Send biz event as message to queue (to be processed from the other function)
            this.bizEventToReceiptService.handleSendMessageToQueue(bizEventList, receipt);
        }

        if (!isReceiptStatusValid(receipt)) {
            // save only if receipt creation fail
            documentdb.setValue(receipt);
        }
    }
}
