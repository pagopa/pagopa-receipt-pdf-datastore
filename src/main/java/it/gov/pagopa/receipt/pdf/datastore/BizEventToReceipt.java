package it.gov.pagopa.receipt.pdf.datastore;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.CosmosDBTrigger;
import com.microsoft.azure.functions.annotation.ExponentialBackoffRetry;
import com.microsoft.azure.functions.annotation.FunctionName;
import it.gov.pagopa.receipt.pdf.datastore.entities.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entities.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entities.receipt.EventData;
import it.gov.pagopa.receipt.pdf.datastore.entities.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entities.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entities.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.datastore.entities.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.service.QueueService;
import it.gov.pagopa.receipt.pdf.datastore.utils.ObjectMapperUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class BizEventToReceipt {

	private final QueueService queueService;

	BizEventToReceipt(QueueService queueService){
		this.queueService = queueService;
	}

	BizEventToReceipt(){
		this.queueService = new QueueService();
	}

	@FunctionName("BizEventToReceiptProcessor")
	@ExponentialBackoffRetry(maxRetryCount = 5, minimumInterval = "500", maximumInterval = "5000")
	public void processBizEventToReceipt(
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
		Logger logger = context.getLogger();

		String msg = String.format("BizEventEnrichment stat %s function - num events triggered %d", context.getInvocationId(),  items.size());
		logger.info(msg);
		int discarder = 0;
		for (BizEvent be: items) {
			
	        if (be.getEventStatus().equals(BizEventStatusType.DONE)) {

				Receipt receipt = new Receipt();

				//insert BizEvent data into receipt
				receipt.setIdEvent(be.getId());

				EventData eventData = new EventData();
				eventData.setPayerFiscalCode(be.getPayer().getEntityUniqueIdentifierValue());
				eventData.setDebtorFiscalCode(be.getDebtor().getEntityUniqueIdentifierValue());
				//TODO define right transaction value
				eventData.setTransactionCreationDate(be.getTransactionDetails().getTransaction().getCreationDate());
				receipt.setEventData(eventData);

	        	String message = String.format("BizEventToReceipt function called at %s for event with id %s and status %s",
		        		LocalDateTime.now(), be.getId(), be.getEventStatus());
		        logger.info(message);

				try {

					String messageText = ObjectMapperUtils.writeValueAsString(be);

					// Add a message to the queue
					Response<SendMessageResult> sendMessageResult = queueService.sendMessageToQueue(messageText);

					if (sendMessageResult.getStatusCode() != 200) {
						receipt.setStatus(ReceiptStatusType.NOT_QUEUE_SENT);
						ReasonError reasonError = new ReasonError(ReasonErrorCode.ERROR_QUEUE.getCode(), "Error sending message to queue");
						receipt.setReasonErr(reasonError);
					} else {
						receipt.setStatus(ReceiptStatusType.INSERTED);
					}


				} catch (Exception e) {
					receipt.setStatus(ReceiptStatusType.NOT_QUEUE_SENT);
					ReasonError reasonError = new ReasonError(ReasonErrorCode.ERROR_QUEUE.getCode(), "Error parsing BizEvent as string at: "+e.getMessage());
					receipt.setReasonErr(reasonError);

					logger.severe("Error parsing BizEvent as string at "+ LocalDateTime.now()+ " : " + e.getMessage());
				}

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

		documentdb.setValue(itemsDone);
	}
}
