package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.azure.storage.queue.models.SendMessageResult;
import it.gov.pagopa.receipt.pdf.datastore.client.CartQueueClient;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptQueueClient;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Client for the Queue
 */
public class CartQueueClientImpl implements CartQueueClient {

    private static CartQueueClientImpl instance;

    private final int cartQueueDelay = Integer.parseInt(System.getenv().getOrDefault("RECEIPT_QUEUE_DELAY", "1"));

    private final QueueClient cartQueueClient;

    private CartQueueClientImpl() {
        String cartQueueConnString = System.getenv("RECEIPT_QUEUE_CONN_STRING");
        String cartQueueTopic = System.getenv("CART_QUEUE_TOPIC");

        this.cartQueueClient = new QueueClientBuilder()
                .connectionString(cartQueueConnString)
                .queueName(cartQueueTopic)
                .buildClient();
    }

    public CartQueueClientImpl(QueueClient cartQueueClient) {
        this.cartQueueClient = cartQueueClient;
    }

    public static CartQueueClientImpl getInstance() {
        if (instance == null) {
            instance = new CartQueueClientImpl();
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

        return this.cartQueueClient.sendMessageWithResponse(
                messageText, Duration.of(cartQueueDelay, ChronoUnit.SECONDS),
                null, null, null);

    }
}
