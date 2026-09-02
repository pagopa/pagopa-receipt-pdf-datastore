package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.azure.storage.queue.models.SendMessageResult;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptQueueClient;
import it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Client for the Queue
 */
public class ReceiptQueueClientImpl implements ReceiptQueueClient {

    private final Logger logger = LoggerFactory.getLogger(ReceiptQueueClientImpl.class);

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
        return SingletonHelper.INSTANCE;
    }

    /**
     * Bill Pugh singleton holder: the JVM guarantees that the class is loaded
     * (and therefore INSTANCE initialized) lazily and in a thread-safe way.
     */
    private static class SingletonHelper {
        private static final ReceiptQueueClientImpl INSTANCE = new ReceiptQueueClientImpl();
    }

    /**
     * Send string message to the queue
     *
     * @param messageText Biz-event encoded to base64 string
     * @return response from the queue
     */
    public Response<SendMessageResult> sendMessageToQueue(String messageText) {
        long start = System.currentTimeMillis();
        try {
            Response<SendMessageResult> resp = this.queueClient.sendMessageWithResponse(
                    messageText, Duration.of(receiptQueueDelay, ChronoUnit.SECONDS),
                    null, null, null);
            LoggingUtils.logIoSuccess(logger, "Published receipt for generation",
                    LoggingUtils.DEP_QUEUE_RECEIPTS, null, start,
                    Map.of(LoggingUtils.DETAILS_STATUS_CODE, resp.getStatusCode()));
            return resp;
        } catch (RuntimeException e) {
            LoggingUtils.logIoFailure(logger, "Error publishing receipt for generation",
                    LoggingUtils.DEP_QUEUE_RECEIPTS, null, start, e, null);
            throw e;
        }
    }
}
