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

public class BizEventToReceipt {

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

        for (BizEvent bizEvent : items) {

            if (bizEvent != null && bizEvent.getEventStatus().equals(BizEventStatusType.DONE)) {

                Receipt receipt = new Receipt();

                //insert BizEvent data into receipt
                receipt.setIdEvent(bizEvent.getId());

                EventData eventData = new EventData();
                eventData.setPayerFiscalCode(bizEvent.getPayer().getEntityUniqueIdentifierValue());
                eventData.setDebtorFiscalCode(bizEvent.getDebtor().getEntityUniqueIdentifierValue());
                //TODO define right transaction value
                eventData.setTransactionCreationDate(
                        bizEvent.getTransactionDetails() != null &&
                                bizEvent.getTransactionDetails().getTransaction() != null
                                ?
                                bizEvent.getTransactionDetails().getTransaction().getCreationDate()
                                : null
                );
                receipt.setEventData(eventData);

                String message = String.format("BizEventToReceipt function called at %s for event with id %s and status %s",
                        LocalDateTime.now(), bizEvent.getId(), bizEvent.getEventStatus());
                logger.info(message);

                handleSendMessageToQueue(bizEvent, receipt);

                itemsDone.add(receipt);

            } else {
                discarder++;
            }

        }
        // discarder
        msg = String.format("itemsDone stat %s function - %d number of events in discarder  ", context.getInvocationId(), discarder);
        logger.info(msg);
        // call the Queue
        msg = String.format("itemsDone stat %s function - number of events in DONE sent to the receipt queue %d", context.getInvocationId(), itemsDone.size());
        logger.info(msg);

        // call the Datastore
        msg = String.format("BizEventToReceipt stat %s function - number of receipts inserted on the datastore %d", context.getInvocationId(), itemsDone.size());
        logger.info(msg);

        if (!itemsDone.isEmpty()) {
            documentdb.setValue(itemsDone);
        }
    }

    private static void handleSendMessageToQueue(BizEvent bizEvent, Receipt receipt) {
        String messageText = Base64.getMimeEncoder().encodeToString(Objects.requireNonNull(ObjectMapperUtils.writeValueAsString(bizEvent)).getBytes());

        ReceiptQueueClientImpl queueService = ReceiptQueueClientImpl.getInstance();

        // Add a message to the queue
        Response<SendMessageResult> sendMessageResult = queueService.sendMessageToQueue(messageText);

        if (sendMessageResult.getStatusCode() != HttpStatus.CREATED.value()) {
            handleError(receipt, "Error sending message to queue");
        } else {
            receipt.setStatus(ReceiptStatusType.INSERTED);
        }

    }

    private static void handleError(Receipt receipt, String e) {
        receipt.setStatus(ReceiptStatusType.NOT_QUEUE_SENT);
        ReasonError reasonError = new ReasonError(ReasonErrorCode.ERROR_QUEUE.getCode(), e);
        receipt.setReasonErr(reasonError);
    }
}
