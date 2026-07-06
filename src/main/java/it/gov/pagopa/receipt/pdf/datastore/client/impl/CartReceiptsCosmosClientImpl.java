package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.implementation.PreconditionFailedException;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import it.gov.pagopa.receipt.pdf.datastore.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.CartReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartConcurrentUpdateException;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

public class CartReceiptsCosmosClientImpl implements CartReceiptsCosmosClient {

    private static CartReceiptsCosmosClientImpl instance;
    private final String databaseId = System.getenv("COSMOS_RECEIPT_DB_NAME");
    private final String cartForReceiptContainerName = System.getenv("CART_FOR_RECEIPT_CONTAINER_NAME");
    private final String cartReceiptsMessageErrorsContainerName = System.getenv("CART_RECEIPTS_MESSAGE_ERRORS_CONTAINER_NAME");

    private static final String DOCUMENT_NOT_FOUND_ERR_MSG = "Document not found in the defined container";

    private final String millisDiff = System.getenv().getOrDefault("MAX_DATE_DIFF_MILLIS", "1800000");
    private final String millisNotifyDif = System.getenv().getOrDefault("MAX_DATE_DIFF_NOTIFY_MILLIS", "1800000");
    private final String numDaysRecoverFailed = System.getenv().getOrDefault("RECOVER_FAILED_MASSIVE_MAX_DAYS", "0");
    private final String numDaysRecoverNotNotified = System.getenv().getOrDefault("RECOVER_NOT_NOTIFIED_MASSIVE_MAX_DAYS", "0");

    private final CosmosClient cosmosClient;

    private CartReceiptsCosmosClientImpl() {
        String azureKey = System.getenv("COSMOS_RECEIPT_KEY");
        String serviceEndpoint = System.getenv("COSMOS_RECEIPT_SERVICE_ENDPOINT");
        String readRegion = System.getenv("COSMOS_RECEIPT_READ_REGION");

        this.cosmosClient = new CosmosClientBuilder()
                .endpoint(serviceEndpoint)
                .key(azureKey)
                .consistencyLevel(ConsistencyLevel.BOUNDED_STALENESS)
                .preferredRegions(List.of(readRegion))
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
    public CartForReceipt getCartItem(String cartId) throws CartNotFoundException {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(cartForReceiptContainerName);

        try {
            return cosmosContainer.readItem(cartId, new PartitionKey(cartId), CartForReceipt.class).getItem();
        } catch (CosmosException e) {
            throw new CartNotFoundException(DOCUMENT_NOT_FOUND_ERR_MSG, e);
        }
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
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(cartReceiptsMessageErrorsContainerName);

        try {
            return cosmosContainer.readItem(cartId, new PartitionKey(cartId), CartReceiptError.class).getItem();
        } catch (CosmosException e) {
            throw new CartNotFoundException(DOCUMENT_NOT_FOUND_ERR_MSG, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<FeedResponse<CartForReceipt>> getFailedCartReceiptDocuments(
            String continuationToken,
            Integer pageSize
    ) {
        long daysAgo = OffsetDateTime.now()
                .truncatedTo(ChronoUnit.DAYS)
                .minusDays(Long.parseLong(numDaysRecoverFailed))
                .toInstant()
                .toEpochMilli();

        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c " +
                        "WHERE (c.status = @statusFailed OR c.status = @statusNotQueueSent) " +
                        "   AND c.inserted_at >= @minInsertedAt ",
                Arrays.asList(
                        new SqlParameter("@statusFailed", CartStatusType.FAILED.name()),
                        new SqlParameter("@statusNotQueueSent", CartStatusType.NOT_QUEUE_SENT.name()),
                        new SqlParameter("@minInsertedAt", daysAgo)
                )
        );

        return executePagedQuery(cartForReceiptContainerName, querySpec, continuationToken, pageSize);
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
        long daysAgo = currentDateTime
                .truncatedTo(ChronoUnit.DAYS)
                .minusDays(Long.parseLong(numDaysRecoverFailed))
                .toInstant()
                .toEpochMilli();

        // (now - c.inserted_at) >= millisDiff  <=>  c.inserted_at <= now - millisDiff
        long maxInsertedAt = currentDateTime.toInstant().toEpochMilli() - Long.parseLong(millisDiff);

        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c " +
                        "WHERE (c.status = @statusInserted OR c.status = @statusWaitingForBizEvent) " +
                        "  AND c.inserted_at >= @minInsertedAt " +
                        "  AND c.inserted_at <= @maxInsertedAt",
                Arrays.asList(
                        new SqlParameter("@statusInserted", CartStatusType.INSERTED.name()),
                        new SqlParameter("@statusWaitingForBizEvent", CartStatusType.WAITING_FOR_BIZ_EVENT.name()),
                        new SqlParameter("@minInsertedAt", daysAgo),
                        new SqlParameter("@maxInsertedAt", maxInsertedAt)
                )
        );

        return executePagedQuery(cartForReceiptContainerName, querySpec, continuationToken, pageSize);
    }

    @Override
    public Iterable<FeedResponse<CartForReceipt>> getIOErrorToNotifyCartReceiptDocuments(
            String continuationToken,
            Integer pageSize
    ) {
        long daysAgo = OffsetDateTime.now()
                .truncatedTo(ChronoUnit.DAYS)
                .minusDays(Long.parseLong(numDaysRecoverNotNotified))
                .toInstant()
                .toEpochMilli();

        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c " +
                        "WHERE c.status = @statusIoErrorToNotify " +
                        "  AND c.generated_at >= @generatedAt",
                Arrays.asList(
                        new SqlParameter("@statusIoErrorToNotify", CartStatusType.IO_ERROR_TO_NOTIFY.name()),
                        new SqlParameter("@generatedAt", daysAgo)
                )
        );

        return executePagedQuery(cartForReceiptContainerName, querySpec, continuationToken, pageSize);
    }

    @Override
    public Iterable<FeedResponse<CartForReceipt>> getGeneratedCartReceiptDocuments(
            String continuationToken,
            Integer pageSize
    ) {
        OffsetDateTime currentDateTime = OffsetDateTime.now();
        long daysAgo = currentDateTime
                .truncatedTo(ChronoUnit.DAYS)
                .minusDays(Long.parseLong(numDaysRecoverNotNotified))
                .toInstant()
                .toEpochMilli();

        // (now - c.generated_at) >= millisNotifyDif  <=>  c.generated_at <= now - millisNotifyDif
        long maxGeneratedAt = currentDateTime.toInstant().toEpochMilli() - Long.parseLong(millisNotifyDif);

        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c " +
                        "WHERE c.status = @statusGenerated " +
                        "  AND c.generated_at >= @minGeneratedAt " +
                        "  AND c.generated_at <= @maxGeneratedAt",
                Arrays.asList(
                        new SqlParameter("@statusGenerated", CartStatusType.GENERATED.name()),
                        new SqlParameter("@minGeneratedAt", daysAgo),
                        new SqlParameter("@maxGeneratedAt", maxGeneratedAt)
                )
        );

        return executePagedQuery(cartForReceiptContainerName, querySpec, continuationToken, pageSize);
    }

    /**
     * PRIVATE METHODS
     */

    private Iterable<FeedResponse<CartForReceipt>> executePagedQuery(
            String containerName,
            SqlQuerySpec querySpec,
            String continuationToken,
            Integer pageSize
    ) {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        return cosmosDatabase.getContainer(containerName)
                .queryItems(querySpec, new CosmosQueryRequestOptions(), CartForReceipt.class)
                .iterableByPage(continuationToken, pageSize);
    }
}
