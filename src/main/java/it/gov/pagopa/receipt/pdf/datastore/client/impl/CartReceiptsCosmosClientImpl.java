package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.cosmos.ConsistencyLevel;
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


    private static final String DOCUMENT_NOT_FOUND_ERR_MSG = "Document not found in the defined container";

    private final String millisDiff = System.getenv().getOrDefault("MAX_DATE_DIFF_MILLIS", "1800000");
    private final String millisNotifyDif = System.getenv().getOrDefault("MAX_DATE_DIFF_NOTIFY_MILLIS", "1800000");
    private final String numDaysRecoverFailed = System.getenv().getOrDefault("RECOVER_FAILED_MASSIVE_MAX_DAYS", "0");
    private final String numDaysRecoverNotNotified = System.getenv().getOrDefault("RECOVER_NOT_NOTIFIED_MASSIVE_MAX_DAYS", "0");

    private final CosmosContainer cartForReceiptContainer;
    private final CosmosContainer cartReceiptsErrorsContainer;

    @SuppressWarnings("resource") // CosmosClient lifecycle == singleton lifecycle; never closed on purpose
    private CartReceiptsCosmosClientImpl() {
        String azureKey = System.getenv("COSMOS_RECEIPT_KEY");
        String serviceEndpoint = System.getenv("COSMOS_RECEIPT_SERVICE_ENDPOINT");
        String readRegion = System.getenv("COSMOS_RECEIPT_READ_REGION");

        String databaseId = System.getenv("COSMOS_RECEIPT_DB_NAME");
        String cartForReceiptContainerName = System.getenv("CART_FOR_RECEIPT_CONTAINER_NAME");
        String cartReceiptsMessageErrorsContainerName = System.getenv("CART_RECEIPTS_MESSAGE_ERRORS_CONTAINER_NAME");

        CosmosDatabase database = new CosmosClientBuilder()
                .endpoint(serviceEndpoint)
                .key(azureKey)
                .consistencyLevel(ConsistencyLevel.BOUNDED_STALENESS)
                .preferredRegions(List.of(readRegion))
                .buildClient()
                .getDatabase(databaseId);

        this.cartForReceiptContainer = database.getContainer(cartForReceiptContainerName);
        this.cartReceiptsErrorsContainer = database.getContainer(cartReceiptsMessageErrorsContainerName);
    }

    /**
     * Test-only constructor. Package-private visibility so it is only reachable from tests
     * in the same package.
     */
    CartReceiptsCosmosClientImpl(
            CosmosContainer cartForReceiptContainer,
            CosmosContainer cartReceiptsErrorsContainer
    ) {
        this.cartForReceiptContainer = cartForReceiptContainer;
        this.cartReceiptsErrorsContainer = cartReceiptsErrorsContainer;
    }

    public static CartReceiptsCosmosClientImpl getInstance() {
        return SingletonHelper.INSTANCE;
    }

    /**
     * Bill Pugh singleton holder: the JVM guarantees that the class is loaded
     * (and therefore INSTANCE initialized) lazily and in a thread-safe way.
     */
    private static class SingletonHelper {
        private static final CartReceiptsCosmosClientImpl INSTANCE = new CartReceiptsCosmosClientImpl();
    }

    @Override
    public CartForReceipt getCartItem(String cartId) throws CartNotFoundException {
        try {
            return cartForReceiptContainer.readItem(cartId, new PartitionKey(cartId), CartForReceipt.class).getItem();
        } catch (CosmosException e) {
            throw new CartNotFoundException(DOCUMENT_NOT_FOUND_ERR_MSG, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CosmosItemResponse<CartForReceipt> updateCart(CartForReceipt receipt) throws CartConcurrentUpdateException {
        try {
            return cartForReceiptContainer.upsertItem(receipt, new CosmosItemRequestOptions().setIfMatchETag(receipt.get_etag()));
        } catch (PreconditionFailedException e) {
            throw new CartConcurrentUpdateException("The cart has been updated since the last fetch", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CartReceiptError getCartReceiptError(String cartId) throws CartNotFoundException {
        try {
            return cartReceiptsErrorsContainer.readItem(cartId, new PartitionKey(cartId), CartReceiptError.class).getItem();
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

        return executePagedQuery(querySpec, continuationToken, pageSize);
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

        return executePagedQuery(querySpec, continuationToken, pageSize);
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

        return executePagedQuery(querySpec, continuationToken, pageSize);
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

        return executePagedQuery(querySpec, continuationToken, pageSize);
    }

    /**
     * PRIVATE METHODS
     */

    private Iterable<FeedResponse<CartForReceipt>> executePagedQuery(
            SqlQuerySpec querySpec,
            String continuationToken,
            Integer pageSize
    ) {
        return cartForReceiptContainer
                .queryItems(querySpec, new CosmosQueryRequestOptions(), CartForReceipt.class)
                .iterableByPage(continuationToken, pageSize);
    }
}
