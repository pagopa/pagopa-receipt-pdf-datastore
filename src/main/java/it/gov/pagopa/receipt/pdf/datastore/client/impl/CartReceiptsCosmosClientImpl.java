package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.cosmos.*;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.receipt.pdf.datastore.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;

public class CartReceiptsCosmosClientImpl implements CartReceiptsCosmosClient {

    private static CartReceiptsCosmosClientImpl instance;
    private final String databaseId = System.getenv("COSMOS_RECEIPT_DB_NAME");
    private final String cartForReceiptContainerName = System.getenv("CART_FOR_RECEIPT_CONTAINER_NAME");

    private final CosmosClient cosmosClient;

    private CartReceiptsCosmosClientImpl() {
        String azureKey = System.getenv("COSMOS_RECEIPT_KEY");
        String serviceEndpoint = System.getenv("COSMOS_RECEIPT_SERVICE_ENDPOINT");

        this.cosmosClient = new CosmosClientBuilder()
                .endpoint(serviceEndpoint)
                .key(azureKey)
                .consistencyLevel(ConsistencyLevel.STRONG)
                .buildClient();
    }

    public CartReceiptsCosmosClientImpl(CosmosClient cosmosClient) {
        this.cosmosClient = cosmosClient;
    }

    public static CartReceiptsCosmosClientImpl getInstance() {
        if (instance == null) {
            instance = new CartReceiptsCosmosClientImpl();
        }

        return instance;
    }

    /**
     * Retrieve receipt document from CosmosDB database
     *
     * @param eventId Biz-event id
     * @return receipt document
     * @throws ReceiptNotFoundException in case no receipt has been found with the given idEvent
     */
    @Override
    public CartForReceipt getCartItem(String eventId) throws CartNotFoundException {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);

        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(cartForReceiptContainerName);

        //Build query
        String query = "SELECT * FROM c WHERE c.eventId = '%s'".formatted(eventId);

        //Query the container
        CosmosPagedIterable<CartForReceipt> queryResponse = cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), CartForReceipt.class);

        if (queryResponse.iterator().hasNext()) {
            return queryResponse.iterator().next();
        } else {
            throw new CartNotFoundException("Document not found in the defined container");
        }

    }

    /**
     * Save Cart For Receipt on CosmosDB database
     *
     * @param receipt Cart Data to save
     * @return cart-to-receipts documents
     */
    @Override
    public CosmosItemResponse<CartForReceipt> saveCart(CartForReceipt receipt)  {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(cartForReceiptContainerName);
        return cosmosContainer.createItem(receipt);
    }

    /**
     * Update Cart For Receipt on CosmosDB database
     *
     * @param receipt Cart Data to save
     * @return cart-to-receipts documents
     */
    @Override
    public CosmosItemResponse<CartForReceipt> updateCart(CartForReceipt receipt)  {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(cartForReceiptContainerName);
        return cosmosContainer.upsertItem(receipt);
    }

}
