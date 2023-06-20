package it.gov.pagopa.receipt.pdf.datastore;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.CosmosDBTrigger;
import com.microsoft.azure.functions.annotation.ExponentialBackoffRetry;
import com.microsoft.azure.functions.annotation.FunctionName;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.utils.ObjectMapperUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
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
     *      - INSERTED if sending the message to the queue succeeded
     *      - NOT_QUEUE_SENT if sending the message to the queue failed
     * #
     * @param items -> biz-events that triggered the function from the Cosmos database
     * @param documentdb -> output binding that will save the receipt data retrieved from the biz-events
     * @param context -> function context
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

        String msg = String.format("BizEventEnrichment stat %s function - num events triggered %d", context.getInvocationId(), items.size());
        logger.info(msg);
        int discarder = 0;

        //Retrieve receipt data from biz-event
        for (BizEvent bizEvent : items) {

            //Process only biz-event in status DONE
            if (bizEvent != null && bizEvent.getEventStatus().equals(BizEventStatusType.DONE)) {

                Receipt receipt = new Receipt();

                //Insert biz-event data into receipt
                receipt.setIdEvent(bizEvent.getId());

                EventData eventData = new EventData();
                eventData.setPayerFiscalCode(bizEvent.getPayer().getEntityUniqueIdentifierValue());
                eventData.setDebtorFiscalCode(bizEvent.getDebtor().getEntityUniqueIdentifierValue());
                eventData.setTransactionCreationDate(
                        getTransactionCreationDate(bizEvent)
                );

                receipt.setEventData(eventData);

                String message = String.format("BizEventToReceipt function called at %s for event with id %s and status %s",
                        LocalDateTime.now(), bizEvent.getId(), bizEvent.getEventStatus());
                logger.info(message);

                //Send biz event as message to queue (to be processed from the other function)
                handleSendMessageToQueue(bizEvent, receipt);

                //Add receipt to items to be saved on CosmosDB
                itemsDone.add(receipt);

            } else {
                //Discard biz events not in status DONE
                discarder++;
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


    /**
     * Handles sending biz-events as message to queue and updates receipt's status
     *
     * @param bizEvent -> biz-event from CosmosDB
     * @param receipt -> receipt to update
     */
    private static void handleSendMessageToQueue(BizEvent bizEvent, Receipt receipt) {
        //Encode biz-event to base64 string
        String messageText = Base64.getMimeEncoder().encodeToString(Objects.requireNonNull(ObjectMapperUtils.writeValueAsString(bizEvent)).getBytes());

        ReceiptQueueClientImpl queueService = ReceiptQueueClientImpl.getInstance();

        //Add message to the queue
        try{
            Response<SendMessageResult> sendMessageResult = queueService.sendMessageToQueue(messageText);

            if (sendMessageResult.getStatusCode() != HttpStatus.CREATED.value()) {
                handleError(receipt);
            } else {
                receipt.setStatus(ReceiptStatusType.INSERTED);
            }
        } catch (Exception e) {
            handleError(receipt);
        }
    }

    /**
     * Handles errors for queue and updates receipt's status accordingly
     *
     * @param receipt -> receipt to update
     */
    private static void handleError(Receipt receipt) {
        receipt.setStatus(ReceiptStatusType.NOT_QUEUE_SENT);
        ReasonError reasonError = new ReasonError(ReasonErrorCode.ERROR_QUEUE.getCode(), "Error sending message to queue");
        receipt.setReasonErr(reasonError);
    }

    /**
     * Retrieve conditionally the transaction creation date from biz-event
     *
     * @param bizEvent -> biz-event from CosmosDB
     * @return transaction date
     */
    private static String getTransactionCreationDate(BizEvent bizEvent) {
        if (bizEvent != null) {
            if (bizEvent.getTransactionDetails() != null && bizEvent.getTransactionDetails().getTransaction() != null) {
                return bizEvent.getTransactionDetails().getTransaction().getCreationDate();

            } else if (bizEvent.getPaymentInfo() != null) {
                return bizEvent.getPaymentInfo().getPaymentDateTime();
            }
        }

        return null;
    }
}
