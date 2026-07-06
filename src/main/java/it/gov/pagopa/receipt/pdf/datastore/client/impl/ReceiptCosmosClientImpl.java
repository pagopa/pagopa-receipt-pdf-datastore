package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Client for the CosmosDB database
 */
public class ReceiptCosmosClientImpl implements ReceiptCosmosClient {

    private static ReceiptCosmosClientImpl instance;

    private static final String DOCUMENT_NOT_FOUND_ERR_MSG = "Document not found in the defined container";

    private final String millisDiff = System.getenv().getOrDefault("MAX_DATE_DIFF_MILLIS", "1800000");
    private final String millisNotifyDif = System.getenv().getOrDefault("MAX_DATE_DIFF_NOTIFY_MILLIS", "1800000");
    private final String numDaysRecoverFailed = System.getenv().getOrDefault("RECOVER_FAILED_MASSIVE_MAX_DAYS", "0");
    private final String numDaysRecoverNotNotified = System.getenv().getOrDefault("RECOVER_NOT_NOTIFIED_MASSIVE_MAX_DAYS", "0");

    private final CosmosClient cosmosClient;
    private final CosmosContainer receiptContainer;
    private final CosmosContainer receiptErrorContainer;

    private ReceiptCosmosClientImpl() {
        String azureKey = System.getenv("COSMOS_RECEIPT_KEY");
        String serviceEndpoint = System.getenv("COSMOS_RECEIPT_SERVICE_ENDPOINT");
        String readRegion = System.getenv("COSMOS_RECEIPT_READ_REGION");

        this.cosmosClient = new CosmosClientBuilder()
                .endpoint(serviceEndpoint)
                .key(azureKey)
                .preferredRegions(List.of(readRegion))
                .buildClient();

        String databaseId = System.getenv("COSMOS_RECEIPT_DB_NAME");
        String containerId = System.getenv("COSMOS_RECEIPT_CONTAINER_NAME");
        String containerReceiptErrorId = System.getenv()
                .getOrDefault("COSMOS_RECEIPT_ERROR_CONTAINER_NAME", "receipts-message-errors");

        CosmosDatabase database = this.cosmosClient.getDatabase(databaseId);
        this.receiptContainer = database.getContainer(containerId);
        this.receiptErrorContainer = database.getContainer(containerReceiptErrorId);
    }

    /**
     * Test-only constructor. Package-private visibility so it is only reachable from tests
     * in the same package.
     */
    ReceiptCosmosClientImpl(
            CosmosClient cosmosClient,
            CosmosContainer receiptContainer,
            CosmosContainer receiptErrorContainer
    ) {
        this.cosmosClient = cosmosClient;
        this.receiptContainer = receiptContainer;
        this.receiptErrorContainer = receiptErrorContainer;
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
        try {
            return receiptContainer.readItem(eventId, new PartitionKey(eventId), Receipt.class)
                    .getItem();
        } catch (CosmosException e) {
            if (e.getStatusCode() != 404) {
                throw new ReceiptNotFoundException(DOCUMENT_NOT_FOUND_ERR_MSG, e);
            }
            // if not found use fallback query
            SqlQuerySpec querySpec = new SqlQuerySpec(
                    "SELECT * FROM c WHERE c.eventId = @eventId",
                    List.of(new SqlParameter("@eventId", eventId))
            );

            return getDocumentByFilter(receiptContainer, querySpec, Receipt.class)
                    .orElseThrow(() -> new ReceiptNotFoundException(DOCUMENT_NOT_FOUND_ERR_MSG));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReceiptError getReceiptError(String bizEventId) throws ReceiptNotFoundException {
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.bizEventId = @bizEventId",
                List.of(new SqlParameter("@bizEventId", bizEventId))
        );

        return getDocumentByFilter(receiptErrorContainer, querySpec, ReceiptError.class)
                .orElseThrow(() -> new ReceiptNotFoundException(DOCUMENT_NOT_FOUND_ERR_MSG));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<FeedResponse<Receipt>> getFailedReceiptDocuments(String continuationToken, Integer pageSize) {
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
                        new SqlParameter("@statusFailed", ReceiptStatusType.FAILED.name()),
                        new SqlParameter("@statusNotQueueSent", ReceiptStatusType.NOT_QUEUE_SENT.name()),
                        new SqlParameter("@minInsertedAt", daysAgo)
                )
        );

        return executePagedQuery(querySpec, continuationToken, pageSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CosmosItemResponse<Receipt> saveReceipts(Receipt receipt) {
        return receiptContainer.createItem(receipt);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CosmosItemResponse<Receipt> updateReceipts(Receipt receipt) {
        return receiptContainer.upsertItem(receipt);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<FeedResponse<Receipt>> getGeneratedReceiptDocuments(String continuationToken, Integer pageSize) {
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
                        new SqlParameter("@statusGenerated", ReceiptStatusType.GENERATED.name()),
                        new SqlParameter("@minGeneratedAt", daysAgo),
                        new SqlParameter("@maxGeneratedAt", maxGeneratedAt)
                )
        );

        return executePagedQuery(querySpec, continuationToken, pageSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<FeedResponse<Receipt>> getIOErrorToNotifyReceiptDocuments(
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
                        new SqlParameter("@statusIoErrorToNotify", ReceiptStatusType.IO_ERROR_TO_NOTIFY.name()),
                        new SqlParameter("@generatedAt", daysAgo)
                )
        );

        return executePagedQuery(querySpec, continuationToken, pageSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<FeedResponse<Receipt>> getInsertedReceiptDocuments(String continuationToken, Integer pageSize) {
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
                        "WHERE c.status = @statusInserted " +
                        "  AND c.inserted_at >= @minInsertedAt " +
                        "  AND c.inserted_at <= @maxInsertedAt",
                Arrays.asList(
                        new SqlParameter("@statusInserted", ReceiptStatusType.INSERTED.name()),
                        new SqlParameter("@minInsertedAt", daysAgo),
                        new SqlParameter("@maxInsertedAt", maxInsertedAt)
                )
        );

        return executePagedQuery(querySpec, continuationToken, pageSize);
    }

    /**
     * PRIVATE METHODS
     */

    private <T> Optional<T> getDocumentByFilter(CosmosContainer container, SqlQuerySpec querySpec, Class<T> classType) {
        // use stream() to convert iterable and find first element
        return container
                .queryItems(querySpec, new CosmosQueryRequestOptions(), classType)
                .stream()
                .findFirst();
    }

    private Iterable<FeedResponse<Receipt>> executePagedQuery(
            SqlQuerySpec querySpec,
            String continuationToken,
            Integer pageSize
    ) {
        return receiptContainer
                .queryItems(querySpec, new CosmosQueryRequestOptions(), Receipt.class)
                .iterableByPage(continuationToken, pageSize);
    }

}
