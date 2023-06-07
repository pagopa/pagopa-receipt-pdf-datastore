package it.gov.pagopa.receipt.pdf.datastore;


import com.azure.core.http.rest.Response;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.azure.storage.queue.models.SendMessageResult;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.CosmosDBTrigger;
import com.microsoft.azure.functions.annotation.ExponentialBackoffRetry;
import com.microsoft.azure.functions.annotation.FunctionName;
import it.gov.pagopa.receipt.pdf.datastore.entities.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entities.event.enumeration.StatusType;
import it.gov.pagopa.receipt.pdf.datastore.entities.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.utils.ObjectMapperUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class BizEventToReceipt {

	private final String cosmosReceiptQueueConnString = System.getenv("COSMOS_RECEIPT_QUEUE_CONN_STRING");
	private final String cosmosReceiptQueueTopic = System.getenv("COSMOS_RECEIPT_QUEUE_TOPIC");


	@FunctionName("BizEventToReceiptProcessor")
	@ExponentialBackoffRetry(maxRetryCount = 5, minimumInterval = "500", maximumInterval = "5000")
	public void processBizEventEnrichment(
			@CosmosDBTrigger(
					name = "BizEventDatastore",
					databaseName = "db",
					collectionName = "biz-events",
					leaseCollectionName = "biz-events-leases",
					createLeaseCollectionIfNotExists = true,
					maxItemsPerInvocation=100,
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
		List<BizEvent> itemsToNotify = new ArrayList<>();
		Logger logger = context.getLogger();
		QueueClient queueClient = new QueueClientBuilder()
				.connectionString(cosmosReceiptQueueConnString)
				.queueName(cosmosReceiptQueueTopic)
				.buildClient();

		String msg = String.format("BizEventEnrichment stat %s function - num events triggered %d", context.getInvocationId(),  items.size());
		logger.info(msg);
		int discarder = 0;
		for (BizEvent be: items) {
			
	        if (be.getEventStatus().equals(StatusType.DONE)) {

				Receipt receipt = new Receipt();

	        	String message = String.format("BizEventToReceipt function called at %s for event with id %s and status %s",
		        		LocalDateTime.now(), be.getId(), be.getEventStatus());
		        logger.info(message);

				try {

					String messageText = ObjectMapperUtils.writeValueAsString(be);

					// Add a message to the queue
					Response<SendMessageResult> sendMessageResult = queueClient.sendMessageWithResponse(
							messageText, Duration.of(30, ChronoUnit.SECONDS),
							null, null, null);

					if (sendMessageResult.getStatusCode() != 200) {
						//SET receipt status to NOT_QUEUE_SENT
					}

				} catch (Exception e) {
					//
				}

				itemsDone.add(receipt);
				itemsToNotify.add(be);

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
		try {
			documentdb.setValue(itemsDone);
		} catch (NullPointerException e) {
			logger.severe("NullPointerException exception on cosmos receipts msg ingestion at "+ LocalDateTime.now()+ " : " + e.getMessage());
		} catch (Exception e) {
			logger.severe("Generic exception on cosmos receipts msg ingestion at "+ LocalDateTime.now()+ " : " + e.getMessage());
		}
	}

}
