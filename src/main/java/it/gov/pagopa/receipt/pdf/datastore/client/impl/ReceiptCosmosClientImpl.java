package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.*;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;

/**
 * Client for the CosmosDB database
 */
public class ReceiptCosmosClientImpl implements ReceiptCosmosClient {

    private static ReceiptCosmosClientImpl instance;

    private final String databaseId = System.getenv("COSMOS_RECEIPT_DB_NAME");
    private final String containerId = System.getenv("COSMOS_RECEIPT_CONTAINER_NAME");

    private final CosmosClient cosmosClient;

    private ReceiptCosmosClientImpl() {
        String azureKey = System.getenv("COSMOS_RECEIPT_KEY");
        String serviceEndpoint = System.getenv("COSMOS_RECEIPT_SERVICE_ENDPOINT");

        this.cosmosClient = new CosmosClientBuilder()
                .endpoint(serviceEndpoint)
                .key(azureKey)
                .buildClient();
    }

    public ReceiptCosmosClientImpl(CosmosClient cosmosClient) {
        this.cosmosClient = cosmosClient;
    }

    public static ReceiptCosmosClientImpl getInstance() {
        if (instance == null) {
            instance = new ReceiptCosmosClientImpl();
        }

        return instance;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Receipt getReceiptDocument(String eventId) throws ReceiptNotFoundException {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);

        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        //Build query
        String query = String.format("SELECT * FROM c WHERE c.eventId = '%s'", eventId);

        //Query the container
        CosmosPagedIterable<Receipt> queryResponse = cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), Receipt.class);

        if (queryResponse.iterator().hasNext()) {
            return queryResponse.iterator().next();
        }
        throw new ReceiptNotFoundException("Document not found in the defined container");
    }

    /**
     * Retrieve failed receipt documents from CosmosDB database
     *
     * @param continuationToken Paged query continuation token
     * @return receipt documents
     */
    @Override
    public Iterable<FeedResponse<Receipt>> getFailedReceiptDocuments(String continuationToken, Integer pageSize)  {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);

        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        //Build query
        String query = "SELECT *CosmosPagedIterable<Receipt> FROM c WHERE c.status = 'FAILED'";

        //Query the container
        return cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), Receipt.class)
                .iterableByPage(continuationToken,pageSize);

    }

    /**
     * Save Receipts on CosmosDB database
     *
     * @param receipt Receipts to save
     * @return receipt documents
     */
    @Override
    public CosmosItemResponse<Receipt> saveReceipts(Receipt receipt)  {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);

        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        return cosmosContainer.createItem(receipt);
    }

}
