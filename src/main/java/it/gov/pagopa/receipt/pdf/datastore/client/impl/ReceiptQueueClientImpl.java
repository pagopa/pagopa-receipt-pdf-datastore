package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.azure.storage.queue.models.SendMessageResult;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptQueueClient;
import lombok.Setter;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Setter
public class ReceiptQueueClientImpl implements ReceiptQueueClient {

    private static ReceiptQueueClientImpl instance;

    private final String receiptQueueConnString = System.getenv("RECEIPT_QUEUE_CONN_STRING");
    private final String receiptQueueTopic = System.getenv("RECEIPT_QUEUE_TOPIC");
    private final int receiptQueueDelay = Integer.parseInt(System.getenv().getOrDefault("RECEIPT_QUEUE_DELAY", "1"));

    private ReceiptQueueClientImpl(){}

    public static ReceiptQueueClientImpl getInstance(){
        if(instance == null){
            instance = new ReceiptQueueClientImpl();
        }

        return instance;
    }

    public Response<SendMessageResult> sendMessageToQueue(String messageText) {
        QueueClient queueClient = new QueueClientBuilder()
                .connectionString(receiptQueueConnString)
                .queueName(receiptQueueTopic)
                .buildClient();

        return queueClient.sendMessageWithResponse(
                messageText, Duration.of(receiptQueueDelay, ChronoUnit.SECONDS),
                null, null, null);

    }
}
