package it.gov.pagopa.receipt.pdf.datastore.client;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;

public interface CartQueueClient {

    Response<SendMessageResult> sendMessageToQueue(String messageText);
}
