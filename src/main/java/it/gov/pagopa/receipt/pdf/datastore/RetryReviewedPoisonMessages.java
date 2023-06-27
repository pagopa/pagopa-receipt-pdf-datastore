package it.gov.pagopa.receipt.pdf.datastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBTrigger;
import com.microsoft.azure.functions.annotation.ExponentialBackoffRetry;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueOutput;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptErrorStatusType;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Azure Functions with Azure CosmosDB trigger.
 */
public class RetryReviewedPoisonMessages {

    /**
     * This function will be invoked when an CosmosDB trigger occurs
     *
     *
     * @param items      Reviewed Receipt Errors that triggered the function from the Cosmos database
     * @param context    Function context
     */
    @FunctionName("RetryReviewedPoisonMessagesProcessor")
    @ExponentialBackoffRetry(maxRetryCount = 5, minimumInterval = "500", maximumInterval = "5000")
    public void processRetryReviewedPoisonMessages(
            @CosmosDBTrigger(
                    name = "ReceiptErrorDatastore",
                    databaseName = "db",
                    collectionName = "receipts-message-errors",
                    leaseCollectionName = "receipts-message-errors-leases",
                    leaseCollectionPrefix = "materialized",
                    createLeaseCollectionIfNotExists = true,
                    maxItemsPerInvocation = 100,
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            List<ReceiptError> items,
            @QueueOutput(
                    name = "QueueReceiptWaitingForGenOutput",
                    queueName = "%RECEIPT_QUEUE_TOPIC%",
                    connection = "RECEIPT_QUEUE_CONN_STRING")
            OutputBinding<List<String>> requeueMessage,
            final ExecutionContext context) {

        List<String> itemsDone = new ArrayList<>();
        Logger logger = context.getLogger();

        String msg = String.format("BizEventEnrichment stat %s function - num events triggered %d", context.getInvocationId(), items.size());
        logger.info(msg);
        int discarder = 0;

        //Retrieve receipt data from biz-event
        for (ReceiptError receiptError : items) {

            try {
                //Process only biz-event in status DONE
                if (receiptError != null && receiptError.getStatus().equals(ReceiptErrorStatusType.REVIEWED)) {
                    itemsDone.add(receiptError.getMessagePayload());
                } else {
                    //Discard biz events not in status DONE
                    discarder++;
                }
            } catch (Exception e) {
                discarder++;

                //Error info
                msg = String.format("Error to process receiptError with id %s", receiptError.getId());
                logger.log(Level.SEVERE, msg, e);
            }
        }

        //Save receipts data to CosmosDB
        if (!itemsDone.isEmpty()) {
            requeueMessage.setValue(itemsDone);
        }

    }
}
