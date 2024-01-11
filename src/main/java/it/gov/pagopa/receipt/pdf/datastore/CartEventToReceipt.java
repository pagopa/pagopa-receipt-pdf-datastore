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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
                    collectionName = "cart-for-receipts",
                    leaseCollectionName = "cart-for-receipts-leases",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING",
                    createLeaseCollectionIfNotExists = true)
            List<CartForReceipt> listCartForReceipt,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    collectionName = "receipts",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<Receipt>> receiptDocumentdb,
            @CosmosDBOutput(
                    name = "CartReceiptDatastoreOutput",
                    databaseName = "db",
                    collectionName = "cart-for-receipts",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<CartForReceipt>> cartForReceiptDocumentdb,
            final ExecutionContext context
    ) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());
        List<CartForReceipt> listOfCartEvents = new ArrayList<>();
        List<Receipt> listOfReceipt = new ArrayList<>();

        for(CartForReceipt cartForReceipt : listCartForReceipt){

            if (cartForReceipt.getTotalNotice() != cartForReceipt.getCartPaymentId().size()) {
                logger.info("[{}] Not all items collected for cart with id {}, this event will be skipped",
                        context.getFunctionName(), cartForReceipt.getId());
                continue;
            }

            if (!cartForReceipt.getStatus().equals(CartStatusType.INSERTED)) {
                logger.info("[{}] Cart with id {} not in status {}, this event will be skipped",
                        context.getFunctionName(), cartForReceipt.getId(), CartStatusType.INSERTED);
            } else {
                List<BizEvent> bizEventList;
                Receipt receipt;

                try {
                    bizEventList = this.bizEventToReceiptService.getCartBizEvents(cartForReceipt.getId());
                    receipt = BizEventToReceiptUtils.createCartReceipt(bizEventList, bizEventToReceiptService, logger);

                    if (!isReceiptStatusValid(receipt)) {
                        logger.error("[{}] Failed to process cart with id {}: fail to tokenize fiscal codes",
                                context.getFunctionName(), cartForReceipt.getId());
                        cartForReceipt.setStatus(CartStatusType.FAILED);
                        cartForReceipt.setReasonError(receipt.getReasonErr());
                    } else {
                        // Add receipt to items to be saved on CosmosDB
                        this.bizEventToReceiptService.handleSaveReceipt(receipt);

                        if (!isReceiptStatusValid(receipt)) {
                            logger.error("[{}] Failed to process cart with id {}: fail to save receipt",
                                    context.getFunctionName(), cartForReceipt.getId());
                            cartForReceipt.setStatus(CartStatusType.FAILED);
                            cartForReceipt.setReasonError(receipt.getReasonErr());
                        } else{
                            // Send biz event as message to queue (to be processed from the other function)
                            this.bizEventToReceiptService.handleSendMessageToQueue(bizEventList, receipt);

                            if (!isReceiptStatusValid(receipt)) {
                                // save only if receipt save on cosmos or send on queue fail
                                listOfReceipt.add(receipt);
                            }
                            cartForReceipt.setStatus(CartStatusType.SENT);
                            logger.info("[{}] Cart with id {} processes successfully. Cart with status: {} and receipt with status: {}",
                                    context.getFunctionName(), cartForReceipt.getId(), cartForReceipt.getStatus(), receipt.getStatus());

                        }
                    }
                } catch (Exception e) {
                    cartForReceipt.setStatus(CartStatusType.FAILED);
                    cartForReceipt.setReasonError(ReasonError.builder().code(500).message(e.getMessage()).build());
                }

                listOfCartEvents.add(cartForReceipt);
            }

        }

        if(!listOfCartEvents.isEmpty()){
            cartForReceiptDocumentdb.setValue(listOfCartEvents);
        }

        if(!listOfReceipt.isEmpty()){
            receiptDocumentdb.setValue(listOfReceipt);
        }
    }
}
