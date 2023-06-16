package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;

public class ReceiptCosmosClientImpl implements ReceiptCosmosClient {

    private static ReceiptCosmosClientImpl instance;

    private final String serviceEndpoint = System.getenv("COSMOS_RECEIPT_SERVICE_ENDPOINT");
    private final String azureKey = System.getenv("COSMOS_RECEIPT_KEY");

    private final String databaseId = System.getenv("COSMOS_RECEIPT_DB_NAME");
    private final String containerId = System.getenv("COSMOS_RECEIPT_CONTAINER_NAME");

    public static ReceiptCosmosClientImpl getInstance(){
        if(instance == null){
            instance = new ReceiptCosmosClientImpl();
        }

        return instance;
    }

    public Receipt getReceiptDocument(String receiptId) throws ReceiptNotFoundException {
        CosmosClient cosmosClient = new CosmosClientBuilder()
                .endpoint(serviceEndpoint)
                .key(azureKey)
                .buildClient();

        CosmosDatabase cosmosDatabase = cosmosClient.getDatabase(databaseId);

        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        String query = "SELECT * FROM c WHERE c.idEvent = " + "'" + receiptId + "'";

        CosmosPagedIterable<Receipt> queryResponse = cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), Receipt.class);

        if(queryResponse.iterator().hasNext()){
            return queryResponse.iterator().next();
        } else {
            throw new ReceiptNotFoundException("Document not found in the defined container");
        }

    }
}
