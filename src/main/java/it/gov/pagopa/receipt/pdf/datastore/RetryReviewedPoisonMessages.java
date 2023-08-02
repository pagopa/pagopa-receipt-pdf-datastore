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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;

/**
 * Azure Functions with Azure CosmosDB trigger.
 */
public class RetryReviewedPoisonMessages {

    /**
     * This function will be invoked when an CosmosDB trigger occurs
     *
     * When an updated document in the receipt-message-errors CosmosDB has status REVIEWED attempts
     * to send it back to the provided output topic.
     * If succeeds saves the element with status REQUEUED
     * If fails updates the document back in status TO_REVIEW with an updated error description
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
        Logger logger = LoggerFactory.getLogger(getClass());

        String msg = String.format("[%s] documentCaptorValue stat %s function - num errors reviewed triggered %d",
                context.getFunctionName(), context.getInvocationId(), items.size());
        logger.debug(msg);

        ReceiptQueueClientImpl queueService = ReceiptQueueClientImpl.getInstance();

        //Retrieve receipt data from biz-event
        for (ReceiptError receiptError : items) {

                //Process only errors in status REVIEWED
                if (receiptError != null && receiptError.getStatus().equals(ReceiptErrorStatusType.REVIEWED)) {

                    try {

                        Response<SendMessageResult> sendMessageResult =
                            queueService.sendMessageToQueue(Base64.getMimeEncoder().encodeToString(
                                    receiptError.getMessagePayload().getBytes()));
                        if (sendMessageResult.getStatusCode() != HttpStatus.CREATED.value()) {
                            throw new UnableToQueueException("Unable to queue due to error: " +
                                    sendMessageResult.getStatusCode());
                        }

                        receiptError.setStatus(ReceiptErrorStatusType.REQUEUED);

                    } catch (Exception e) {
                        //Error info
                        msg = String.format("[%s] Error to process receiptError with id %s",
                                context.getFunctionName(), receiptError.getId());
                        logger.error(msg, e);
                        receiptError.setMessageError(e.getMessage());
                        receiptError.setStatus(ReceiptErrorStatusType.TO_REVIEW);
                    }

                    itemsDone.add(receiptError);

               }

        }

        documentdb.setValue(itemsDone);

    }
}
