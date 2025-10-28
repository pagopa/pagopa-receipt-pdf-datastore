package it.gov.pagopa.receipt.pdf.datastore.client;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;

public interface CartQueueClient {

    /**
     * @param messageText the message to send to the queue with the list of bizEvents
     * @return the response from the queue service
     *
     * This method sends a message to the cart queue for generating a PDF receipt.
     */
    Response<SendMessageResult> sendMessageToQueue(String messageText);
}
