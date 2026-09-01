package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.azure.storage.queue.models.SendMessageResult;
import it.gov.pagopa.receipt.pdf.datastore.client.CartQueueClient;
import it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils;
import it.gov.pagopa.receipt.pdf.datastore.utils.PerfTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Client for the Queue
 */
public class CartQueueClientImpl implements CartQueueClient {

    private final Logger logger = LoggerFactory.getLogger(CartQueueClientImpl.class);

    private final int cartQueueDelay = Integer.parseInt(System.getenv().getOrDefault("CART_RECEIPT_QUEUE_DELAY", "1"));

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
        return SingletonHelper.INSTANCE;
    }

    /**
     * Bill Pugh singleton holder: the JVM guarantees that the class is loaded
     * (and therefore INSTANCE initialized) lazily and in a thread-safe way.
     */
    private static class SingletonHelper {
        private static final CartQueueClientImpl INSTANCE = new CartQueueClientImpl();
    }

    /**
     * Send string message to the queue
     *
     * @param messageText Biz-event encoded to base64 string
     * @return response from the queue
     */
    public Response<SendMessageResult> sendMessageToQueue(String messageText) {
        try (PerfTracer t = PerfTracer.start(logger, LoggingUtils.STEP_QUEUE_SEND_CART)) {
            try {
                Response<SendMessageResult> resp = this.cartQueueClient.sendMessageWithResponse(
                        messageText, Duration.of(cartQueueDelay, ChronoUnit.SECONDS),
                        null, null, null);
                t.tag(LoggingUtils.TAG_STATUS_CODE, resp.getStatusCode());
                return resp;
            } catch (RuntimeException e) {
                t.markFailure(e);
                throw e;
            }
        }
    }
}
