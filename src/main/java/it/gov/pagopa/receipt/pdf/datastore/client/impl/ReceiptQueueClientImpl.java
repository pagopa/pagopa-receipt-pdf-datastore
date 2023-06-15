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

    private final String cosmosReceiptQueueConnString = System.getenv("RECEIPT_QUEUE_CONN_STRING");
    private final String cosmosReceiptQueueTopic = System.getenv("RECEIPT_QUEUE_TOPIC");

    public Response<SendMessageResult> sendMessageToQueue(String messageText) {
        QueueClient queueClient = new QueueClientBuilder()
                .connectionString(cosmosReceiptQueueConnString)
                .queueName(cosmosReceiptQueueTopic)
                .buildClient();

        return queueClient.sendMessageWithResponse(
                messageText, Duration.of(30, ChronoUnit.SECONDS),
                null, null, null);

    }
}
