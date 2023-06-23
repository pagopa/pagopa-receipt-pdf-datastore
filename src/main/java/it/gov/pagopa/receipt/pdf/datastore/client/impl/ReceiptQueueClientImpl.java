package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.azure.storage.queue.models.SendMessageResult;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptQueueClient;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Client for the Queue
 */
public class ReceiptQueueClientImpl implements ReceiptQueueClient {

    private static ReceiptQueueClientImpl instance;

    private final int receiptQueueDelay = Integer.parseInt(System.getenv().getOrDefault("RECEIPT_QUEUE_DELAY", "1"));

    private final QueueClient queueClient;

    private ReceiptQueueClientImpl() {
        String receiptQueueConnString = System.getenv("RECEIPT_QUEUE_CONN_STRING");
        String receiptQueueTopic = System.getenv("RECEIPT_QUEUE_TOPIC");

        this.queueClient = new QueueClientBuilder()
                .connectionString(receiptQueueConnString)
                .queueName(receiptQueueTopic)
                .buildClient();
    }

    public ReceiptQueueClientImpl(QueueClient queueClient) {
        this.queueClient = queueClient;
    }

    public static ReceiptQueueClientImpl getInstance() {
        if (instance == null) {
            instance = new ReceiptQueueClientImpl();
        }

        return instance;
    }

    /**
     * Send string message to the queue
     *
     * @param messageText Biz-event encoded to base64 string
     * @return response from the queue
     */
    public Response<SendMessageResult> sendMessageToQueue(String messageText) {

        return this.queueClient.sendMessageWithResponse(
                messageText, Duration.of(receiptQueueDelay, ChronoUnit.SECONDS),
                null, null, null);

    }
}
