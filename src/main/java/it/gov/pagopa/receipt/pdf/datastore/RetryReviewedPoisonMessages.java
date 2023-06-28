package it.gov.pagopa.receipt.pdf.datastore;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.UnableToQueueException;

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
            @CosmosDBOutput(
                    name = "ReceiptMessageErrorsDatastoreOutput",
                    databaseName = "db",
                    collectionName = "receipts-message-errors",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<ReceiptError>> documentdb,
            final ExecutionContext context) {

        List<ReceiptError> itemsDone = new ArrayList<>();
        Logger logger = context.getLogger();

        String msg = String.format("documentCaptorValue stat %s function - num errors reviewed triggered %d", context.getInvocationId(), items.size());
        logger.info(msg);

        ReceiptQueueClientImpl queueService = ReceiptQueueClientImpl.getInstance();

        //Retrieve receipt data from biz-event
        for (ReceiptError receiptError : items) {

                //Process only errors in status REVIEWED
                if (receiptError != null && receiptError.getStatus().equals(ReceiptErrorStatusType.REVIEWED)) {

                    try {

                        Response<SendMessageResult> sendMessageResult =
                            queueService.sendMessageToQueue(receiptError.getMessagePayload());
                        if (sendMessageResult.getStatusCode() != HttpStatus.CREATED.value()) {
                            throw new UnableToQueueException("Unable to queue due to error: " +
                                    sendMessageResult.getStatusCode());
                        }

                        receiptError.setStatus(ReceiptErrorStatusType.REQUEUED);

                    } catch (Exception e) {
                        //Error info
                        msg = String.format("Error to process receiptError with id %s", receiptError.getId());
                        logger.log(Level.SEVERE, msg, e);
                        receiptError.setMessageError(e.getMessage());
                        receiptError.setStatus(ReceiptErrorStatusType.TO_REVIEW);
                    }

                    itemsDone.add(receiptError);

               }

        }

        documentdb.setValue(itemsDone);

    }
}
