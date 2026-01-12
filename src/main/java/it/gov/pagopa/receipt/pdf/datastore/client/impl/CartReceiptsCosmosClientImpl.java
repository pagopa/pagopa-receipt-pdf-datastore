package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.cosmos.*;
import com.azure.cosmos.implementation.PreconditionFailedException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.receipt.pdf.datastore.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.CartReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartConcurrentUpdateException;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;

import java.util.Optional;

public class CartReceiptsCosmosClientImpl implements CartReceiptsCosmosClient {

    private static CartReceiptsCosmosClientImpl instance;
    private final String databaseId = System.getenv("COSMOS_RECEIPT_DB_NAME");
    private final String cartForReceiptContainerName = System.getenv("CART_FOR_RECEIPT_CONTAINER_NAME");
    private final String cartReceiptsMessageErrorsContainerName = System.getenv("CART_RECEIPTS_MESSAGE_ERRORS_CONTAINER_NAME");

    private static final String DOCUMENT_NOT_FOUND_ERR_MSG = "Document not found in the defined container";

    private final CosmosClient cosmosClient;

    private CartReceiptsCosmosClientImpl() {
        String azureKey = System.getenv("COSMOS_RECEIPT_KEY");
        String serviceEndpoint = System.getenv("COSMOS_RECEIPT_SERVICE_ENDPOINT");

        this.cosmosClient = new CosmosClientBuilder()
                .endpoint(serviceEndpoint)
                .key(azureKey)
                .consistencyLevel(ConsistencyLevel.BOUNDED_STALENESS)
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
     * {@inheritDoc}
     */
//    @Override
//    public CartForReceipt getCartItem(String eventId) throws CartNotFoundException {
//        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
//
//        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(cartForReceiptContainerName);
//
//        //Build query
//        String query = "SELECT * FROM c WHERE c.eventId = '%s'".formatted(eventId);
//
//        //Query the container
//        CosmosPagedIterable<CartForReceipt> queryResponse = cosmosContainer
//                .queryItems(query, new CosmosQueryRequestOptions(), CartForReceipt.class);
//
//        if (queryResponse.iterator().hasNext()) {
//            return queryResponse.iterator().next();
//        } else {
//            throw new CartNotFoundException("Document not found in the defined container");
//        }
//    }

    @Override
    public CartForReceipt getCartItem(String eventId) throws CartNotFoundException {
        return getDocumentByFilter(cartForReceiptContainerName, "eventId", eventId, CartForReceipt.class)
            .orElseThrow(() -> new CartNotFoundException(DOCUMENT_NOT_FOUND_ERR_MSG));
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public CosmosItemResponse<CartForReceipt> saveCart(CartForReceipt receipt) {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(cartForReceiptContainerName);
        return cosmosContainer.createItem(receipt);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CosmosItemResponse<CartForReceipt> updateCart(CartForReceipt receipt) throws CartConcurrentUpdateException {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(cartForReceiptContainerName);
        try {
            return cosmosContainer.upsertItem(receipt, new CosmosItemRequestOptions().setIfMatchETag(receipt.get_etag()));
        } catch (PreconditionFailedException e) {
            throw new CartConcurrentUpdateException("The cart has been updated since the last fetch", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CartReceiptError getCartReceiptError(String cartId) throws CartNotFoundException {
        return getDocumentByFilter(cartReceiptsMessageErrorsContainerName, "id", cartId, CartReceiptError.class)
                .orElseThrow(() -> new CartNotFoundException(DOCUMENT_NOT_FOUND_ERR_MSG));
    }


    /**
     * PRIVATE METHODS
     */

    private <T> Optional<T> getDocumentByFilter(String containerId, String propertyName, String propertyValue, Class<T> classType) {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        String query = String.format("SELECT * FROM c WHERE c.%s = '%s'", propertyName, propertyValue);

        // use stream() to convert iterable and find first element
        return cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), classType)
                .stream()
                .findFirst();
    }
}
