package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.implementation.PreconditionFailedException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.datastore.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.CartReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartConcurrentUpdateException;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public class CartReceiptsCosmosClientImpl implements CartReceiptsCosmosClient {

    private static CartReceiptsCosmosClientImpl instance;
    private final String databaseId = System.getenv("COSMOS_RECEIPT_DB_NAME");
    private final String cartForReceiptContainerName = System.getenv("CART_FOR_RECEIPT_CONTAINER_NAME");
    private final String cartReceiptsMessageErrorsContainerName = System.getenv("CART_RECEIPTS_MESSAGE_ERRORS_CONTAINER_NAME");

    private static final String DOCUMENT_NOT_FOUND_ERR_MSG = "Document not found in the defined container";

    private final String millisDiff = System.getenv("MAX_DATE_DIFF_MILLIS");
    private final String millisNotifyDif = System.getenv("MAX_DATE_DIFF_NOTIFY_MILLIS");
    private final String numDaysRecoverFailed = System.getenv().getOrDefault("RECOVER_FAILED_MASSIVE_MAX_DAYS", "0");
    private final String numDaysRecoverNotNotified = System.getenv().getOrDefault("RECOVER_NOT_NOTIFIED_MASSIVE_MAX_DAYS", "0");

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
     * {@inheritDoc}
     */
    @Override
    public Iterable<FeedResponse<CartForReceipt>> getFailedCartReceiptDocuments(
            String continuationToken,
            Integer pageSize
    ) {
        String query = "SELECT * FROM c WHERE c.status = 'FAILED'";
        return executePagedQuery(cartForReceiptContainerName, query, continuationToken, pageSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<FeedResponse<CartForReceipt>> getInsertedCartReceiptDocuments(
            String continuationToken,
            Integer pageSize
    ) {
        OffsetDateTime currentDateTime = OffsetDateTime.now();
        long now = currentDateTime.toInstant().toEpochMilli();
        long daysAgo = currentDateTime.truncatedTo(ChronoUnit.DAYS).minusDays(Long.parseLong(numDaysRecoverFailed)).toInstant().toEpochMilli();

        String query = String.format(
                "SELECT * FROM c WHERE (c.status = '%s' AND c.inserted_at >= %s AND ( %s - c.inserted_at) >= %s)",
                ReceiptStatusType.INSERTED, daysAgo, now, millisDiff);

        return executePagedQuery(cartForReceiptContainerName, query, continuationToken, pageSize);
    }

    @Override
    public Iterable<FeedResponse<CartForReceipt>> getIOErrorToNotifyCartReceiptDocuments(
            String continuationToken,
            Integer pageSize
    ) {
        long daysAgo = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(Long.parseLong(numDaysRecoverNotNotified)).toInstant().toEpochMilli();

        String query = String.format("SELECT * FROM c WHERE c.status = '%s' AND c.generated_at >= %s",
                ReceiptStatusType.IO_ERROR_TO_NOTIFY, daysAgo);

        return executePagedQuery(cartForReceiptContainerName, query, continuationToken, pageSize);
    }

    @Override
    public Iterable<FeedResponse<CartForReceipt>> getGeneratedCartReceiptDocuments(
            String continuationToken,
            Integer pageSize
    ) {
        OffsetDateTime currentDateTime = OffsetDateTime.now();
        long now = currentDateTime.toInstant().toEpochMilli();
        long daysAgo = currentDateTime.truncatedTo(ChronoUnit.DAYS).minusDays(Long.parseLong(numDaysRecoverNotNotified)).toInstant().toEpochMilli();

        String query = String.format("SELECT * FROM c WHERE (c.status = '%s' AND c.generated_at >= %s AND ( %s - c.generated_at) >= %s)",
                ReceiptStatusType.GENERATED, daysAgo, now, millisNotifyDif);

        return executePagedQuery(cartForReceiptContainerName, query, continuationToken, pageSize);
    }

    /**
     * PRIVATE METHODS
     */

    private <T> Optional<T> getDocumentByFilter(
            String containerId,
            String propertyName,
            String propertyValue,
            Class<T> classType
    ) {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        String query = String.format("SELECT * FROM c WHERE c.%s = '%s'", propertyName, propertyValue);

        // use stream() to convert iterable and find first element
        return cosmosContainer
                .queryItems(query, new CosmosQueryRequestOptions(), classType)
                .stream()
                .findFirst();
    }

    private Iterable<FeedResponse<CartForReceipt>> executePagedQuery(
            String containerName,
            String query,
            String continuationToken,
            Integer pageSize
    ) {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        return cosmosDatabase.getContainer(containerName)
                .queryItems(query, new CosmosQueryRequestOptions(), CartForReceipt.class)
                .iterableByPage(continuationToken, pageSize);
    }
}
