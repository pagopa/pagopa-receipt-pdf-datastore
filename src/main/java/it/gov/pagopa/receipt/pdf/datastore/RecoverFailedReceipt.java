package it.gov.pagopa.receipt.pdf.datastore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.receipt.pdf.datastore.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.model.ReceiptFailedRecoveryRequest;
import it.gov.pagopa.receipt.pdf.datastore.service.PDVTokenizerService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.BizEventToReceiptServiceImpl;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.PDVTokenizerServiceImpl;
import it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


/**
 * Azure Functions with Azure Http trigger.
 */
public class RecoverFailedReceipt {

    private final Logger logger = LoggerFactory.getLogger(RecoverFailedReceipt.class);

    private final PDVTokenizerService pdvTokenizerService;

    public RecoverFailedReceipt() {
        this.pdvTokenizerService = new PDVTokenizerServiceImpl();
    }


    /**
     * This function will be invoked when a Http Trigger occurs
     *
     * @return response with HttpStatus.OK
     */
    @FunctionName("RecoverFailedReceipt")
    public HttpResponseMessage run (
            @HttpTrigger(name = "RecoverFailedReceiptTrigger",
                    methods = {HttpMethod.PUT},
                    route = "recoverFailed",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<ReceiptFailedRecoveryRequest>> request,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    collectionName = "receipts",
                    connectionStringSetting = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<Receipt>> documentdb,
            final ExecutionContext context) {

        BizEventToReceiptServiceImpl bizEventToReceiptService = new BizEventToReceiptServiceImpl();

        try {

            ReceiptFailedRecoveryRequest receiptFailedRecoveryRequest = request.getBody().get();

            BizEventCosmosClient bizEventCosmosClient =
                    BizEventCosmosClientImpl.getInstance();
            ReceiptCosmosClient receiptCosmosClient =
                    ReceiptCosmosClientImpl.getInstance();

            BizEvent bizEvent = bizEventCosmosClient.getBizEventDocument(
                    receiptFailedRecoveryRequest.getEventId());

            Receipt receipt;

            try {
                receipt = receiptCosmosClient.getReceiptDocument(
                        receiptFailedRecoveryRequest.getEventId());
            } catch (ReceiptNotFoundException e) {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                        .build();
            }

            if (receipt != null && receipt.getStatus().equals(ReceiptStatusType.FAILED)) {
                bizEventToReceiptService.handleSendMessageToQueue(bizEvent, receipt);
                receipt.setStatus(ReceiptStatusType.INSERTED);
                documentdb.setValue(Collections.singletonList(receipt));
            } {
                receipt = BizEventToReceiptUtils.createReceipt(bizEvent,
                        bizEventToReceiptService, pdvTokenizerService);
                bizEventToReceiptService.handleSendMessageToQueue(bizEvent, receipt);
            }

            return request.createResponseBuilder(HttpStatus.OK)
                    .build();

        } catch (NoSuchElementException | ReceiptNotFoundException exception) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .build();
        } catch (PDVTokenizerException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}