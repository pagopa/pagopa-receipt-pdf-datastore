package it.gov.pagopa.receipt.pdf.datastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.CosmosDBTrigger;
import com.microsoft.azure.functions.annotation.ExponentialBackoffRetry;
import com.microsoft.azure.functions.annotation.FunctionName;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.service.BizEventToReceiptService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Azure Functions with Azure CosmosDB trigger.
 */
public class BizEventToReceipt {

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
     * @param items      Biz-events that triggered the function from the Cosmos database
     * @param documentdb Output binding that will save the receipt data retrieved from the biz-events
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
        Logger logger = context.getLogger();

        String msg = String.format("BizEventToReceipt stat %s function - num events triggered %d", context.getInvocationId(), items.size());
        logger.info(msg);
        int discarder = 0;

        //Retrieve receipt data from biz-event
        for (BizEvent bizEvent : items) {

            try {
                //Process only biz-event in status DONE
                if (bizEvent != null && bizEvent.getEventStatus().equals(BizEventStatusType.DONE)) {
                    BizEventToReceiptService service = new BizEventToReceiptService();

                    Receipt receipt = new Receipt();

                    //Insert biz-event data into receipt
                    receipt.setEventId(bizEvent.getId());

                    EventData eventData = new EventData();
                    //TODO verify if payer's or debtor's CF can be null
                    eventData.setPayerFiscalCode(bizEvent.getPayer() != null ? bizEvent.getPayer().getEntityUniqueIdentifierValue() : null);
                    eventData.setDebtorFiscalCode(bizEvent.getDebtor() != null ? bizEvent.getDebtor().getEntityUniqueIdentifierValue() : null);
                    eventData.setTransactionCreationDate(
                            service.getTransactionCreationDate(bizEvent)
                    );

                    receipt.setEventData(eventData);

                    String message = String.format("[BizEventToReceiptProcessor] function called at %s for event with id %s and status %s",
                            LocalDateTime.now(), bizEvent.getId(), bizEvent.getEventStatus());
                    logger.info(message);

                    //Send biz event as message to queue (to be processed from the other function)
                    service.handleSendMessageToQueue(bizEvent, receipt, logger);

                    //Add receipt to items to be saved on CosmosDB
                    itemsDone.add(receipt);

                } else {
                    //Discard biz events not in status DONE
                    discarder++;
                }
            } catch (Exception e) {
                discarder++;

                //Error info
                msg = String.format("[BizEventToReceiptProcessor] Error to process event with id %s", bizEvent.getId());
                logger.log(Level.SEVERE, msg, e);
            }
        }
        //Discarder info
        msg = String.format("itemsDone stat %s function - %d number of events in discarder  ", context.getInvocationId(), discarder);
        logger.info(msg);

        //Call to queue info
        msg = String.format("itemsDone stat %s function - number of events in DONE sent to the receipt queue %d", context.getInvocationId(), itemsDone.size());
        logger.info(msg);

        //Call to datastore info
        msg = String.format("BizEventToReceipt stat %s function - number of receipts inserted on the datastore %d", context.getInvocationId(), itemsDone.size());
        logger.info(msg);

        //Save receipts data to CosmosDB
        if (!itemsDone.isEmpty()) {
            documentdb.setValue(itemsDone);
        }
    }
}
