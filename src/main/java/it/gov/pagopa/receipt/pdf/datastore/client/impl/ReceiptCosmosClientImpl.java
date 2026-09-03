package it.gov.pagopa.receipt.pdf.datastore.client.impl;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.DEP_COSMOS_RECEIPTS;
import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.DETAILS_FALLBACK;
import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.DETAILS_STATUS_CODE;
import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.MSG_FETCHED_RECEIPT;
import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.logIoFailure;
import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.logIoSuccess;

/**
 * Client for the CosmosDB database
 */
public class ReceiptCosmosClientImpl implements ReceiptCosmosClient {

    private final Logger logger = LoggerFactory.getLogger(ReceiptCosmosClientImpl.class);

    private static final String DOCUMENT_NOT_FOUND_ERR_MSG = "Document not found in the defined container";

    private final String millisDiff = System.getenv().getOrDefault("MAX_DATE_DIFF_MILLIS", "1800000");
    private final String millisNotifyDif = System.getenv().getOrDefault("MAX_DATE_DIFF_NOTIFY_MILLIS", "1800000");
    private final String numDaysRecoverFailed = System.getenv().getOrDefault("RECOVER_FAILED_MASSIVE_MAX_DAYS", "0");
    private final String numDaysRecoverNotNotified = System.getenv().getOrDefault("RECOVER_NOT_NOTIFIED_MASSIVE_MAX_DAYS", "0");

    private final CosmosContainer receiptContainer;
    private final CosmosContainer receiptErrorContainer;

    @SuppressWarnings("resource") // CosmosClient lifecycle == singleton lifecycle; never closed on purpose
    private ReceiptCosmosClientImpl() {
        String azureKey = System.getenv("COSMOS_RECEIPT_KEY");
        String serviceEndpoint = System.getenv("COSMOS_RECEIPT_SERVICE_ENDPOINT");
        String readRegion = System.getenv("COSMOS_RECEIPT_READ_REGION");

        String databaseId = System.getenv("COSMOS_RECEIPT_DB_NAME");
        String containerId = System.getenv("COSMOS_RECEIPT_CONTAINER_NAME");
        String containerReceiptErrorId = System.getenv()
                .getOrDefault("COSMOS_RECEIPT_ERROR_CONTAINER_NAME", "receipts-message-errors");

        CosmosDatabase database = new CosmosClientBuilder()
                .endpoint(serviceEndpoint)
                .key(azureKey)
                .preferredRegions(List.of(readRegion))
                .buildClient()
                .getDatabase(databaseId);

        this.receiptContainer = database.getContainer(containerId);
        this.receiptErrorContainer = database.getContainer(containerReceiptErrorId);
    }

    /**
     * Test-only constructor. Package-private visibility so it is only reachable from tests
     * in the same package.
     */
    ReceiptCosmosClientImpl(
            CosmosContainer receiptContainer,
            CosmosContainer receiptErrorContainer
    ) {
        this.receiptContainer = receiptContainer;
        this.receiptErrorContainer = receiptErrorContainer;
    }

    public static ReceiptCosmosClientImpl getInstance() {
        return SingletonHelper.INSTANCE;
    }

    /**
     * Bill Pugh singleton holder: the JVM guarantees that the class is loaded
     * (and therefore INSTANCE initialized) lazily and in a thread-safe way.
     */
    private static class SingletonHelper {
        private static final ReceiptCosmosClientImpl INSTANCE = new ReceiptCosmosClientImpl();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Receipt getReceiptDocument(String eventId) throws ReceiptNotFoundException {
        long startNanos = System.nanoTime();
        try {
            Receipt receipt = receiptContainer.readItem(eventId, new PartitionKey(eventId), Receipt.class).getItem();
            logIoSuccess(logger, "Found Receipt with point read", DEP_COSMOS_RECEIPTS, null, startNanos, null);
            return receipt;
        } catch (CosmosException e) {
            if (e.getStatusCode() != 404) {
                logIoFailure(logger, MSG_FETCHED_RECEIPT,
                        DEP_COSMOS_RECEIPTS, null, startNanos, e, Map.of(DETAILS_STATUS_CODE, e.getStatusCode()));
                throw e;
            }
        }
        // fallback query when read-by-id returns 404
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.eventId = @eventId",
                List.of(new SqlParameter("@eventId", eventId))
        );
        Optional<Receipt> optionalReceipt = getDocumentByFilter(receiptContainer, querySpec, Receipt.class);
        logIoSuccess(logger, "Found Receipt with query",
                DEP_COSMOS_RECEIPTS, null, startNanos, Map.of(DETAILS_FALLBACK, true));
        return optionalReceipt.orElseThrow(() -> new ReceiptNotFoundException(DOCUMENT_NOT_FOUND_ERR_MSG));
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
                List.of(
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
        long startNanos = System.nanoTime();
        try {
            CosmosItemResponse<Receipt> resp = receiptContainer.createItem(receipt);
            logIoSuccess(logger, "Receipt saved", DEP_COSMOS_RECEIPTS, null, startNanos, null);
            return resp;
        } catch (RuntimeException e) {
            logIoFailure(logger, "Error saving receipt", DEP_COSMOS_RECEIPTS, null, startNanos, e, null);
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CosmosItemResponse<Receipt> updateReceipts(Receipt receipt) {
        long startNanos = System.nanoTime();
        try {
            CosmosItemResponse<Receipt> resp = receiptContainer.upsertItem(receipt);
            logIoSuccess(logger, "Receipt updated", DEP_COSMOS_RECEIPTS, null, startNanos, null);
            return resp;
        } catch (RuntimeException e) {
            logIoFailure(logger, "Error updating receipt", DEP_COSMOS_RECEIPTS, null, startNanos, e, null);
            throw e;
        }
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
                List.of(
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
                List.of(
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
                List.of(
                        new SqlParameter("@statusInserted", ReceiptStatusType.INSERTED.name()),
                        new SqlParameter("@minInsertedAt", daysAgo),
                        new SqlParameter("@maxInsertedAt", maxInsertedAt)
                )
        );

        return executePagedQuery(querySpec, continuationToken, pageSize);
    }

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
