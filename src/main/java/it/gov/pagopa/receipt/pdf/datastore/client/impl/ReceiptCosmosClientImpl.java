package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.*;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Client for the CosmosDB database
 */
public class ReceiptCosmosClientImpl implements ReceiptCosmosClient {

    private static ReceiptCosmosClientImpl instance;

    private final String databaseId = System.getenv("COSMOS_RECEIPT_DB_NAME");
    private final String containerId = System.getenv("COSMOS_RECEIPT_CONTAINER_NAME");
    private final String containerReceiptErrorId = System.getenv().getOrDefault("COSMOS_RECEIPT_ERROR_CONTAINER_NAME", "receipts-message-errors");

    private static final String DOCUMENT_NOT_FOUND_ERR_MSG = "Document not found in the defined container";

    private final String millisDiff = System.getenv("MAX_DATE_DIFF_MILLIS");
    private final String millisNotifyDif = System.getenv("MAX_DATE_DIFF_NOTIFY_MILLIS");
    private final String numDaysRecoverFailed = System.getenv().getOrDefault("RECOVER_FAILED_MASSIVE_MAX_DAYS", "0");
    private final String numDaysRecoverNotNotified = System.getenv().getOrDefault("RECOVER_NOT_NOTIFIED_MASSIVE_MAX_DAYS", "0");

    private final CosmosClient cosmosClient;

    private ReceiptCosmosClientImpl() {
        String azureKey = System.getenv("COSMOS_RECEIPT_KEY");
        String serviceEndpoint = System.getenv("COSMOS_RECEIPT_SERVICE_ENDPOINT");
        String readRegion = System.getenv("COSMOS_RECEIPT_READ_REGION");

        this.cosmosClient = new CosmosClientBuilder()
                .endpoint(serviceEndpoint)
                .key(azureKey)
                .preferredRegions(List.of(readRegion))
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
        try {
            CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
            CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

            CosmosItemResponse<Receipt> response = cosmosContainer.readItem(
                    eventId,
                    new PartitionKey(eventId),
                    Receipt.class
            );
            return response.getItem();
        } catch (com.azure.cosmos.CosmosException e) {
            if (e.getStatusCode() == 404) {
                throw new ReceiptNotFoundException(DOCUMENT_NOT_FOUND_ERR_MSG);
            }
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReceiptError getReceiptError(String bizEventId) throws  ReceiptNotFoundException {
        return getDocumentByFilter(containerReceiptErrorId, "bizEventId", bizEventId, ReceiptError.class)
                .orElseThrow(() -> new ReceiptNotFoundException(DOCUMENT_NOT_FOUND_ERR_MSG));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<FeedResponse<Receipt>> getFailedReceiptDocuments(String continuationToken, Integer pageSize) {
        String query = String.format("SELECT * FROM c WHERE (c.status = '%s' or c.status = '%s') AND c.inserted_at >= %s",
                ReceiptStatusType.FAILED, ReceiptStatusType.NOT_QUEUE_SENT,
                OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(
                        Long.parseLong(numDaysRecoverFailed)).toInstant().toEpochMilli());

        return executePagedQuery(containerId, query, Receipt.class, continuationToken, pageSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CosmosItemResponse<Receipt> saveReceipts(Receipt receipt) {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);

        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        return cosmosContainer.createItem(
                receipt,
                new PartitionKey(receipt.getEventId()),
                new CosmosItemRequestOptions()
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CosmosItemResponse<Receipt> updateReceipts(Receipt receipt) {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        CosmosContainer cosmosContainer = cosmosDatabase.getContainer(containerId);

        return cosmosContainer.upsertItem(
                receipt,
                new PartitionKey(receipt.getEventId()),
                new CosmosItemRequestOptions()
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<FeedResponse<Receipt>> getGeneratedReceiptDocuments(String continuationToken, Integer pageSize)  {
        OffsetDateTime currentDateTime = OffsetDateTime.now();
        long now = currentDateTime.toInstant().toEpochMilli();
        long daysAgo = currentDateTime.truncatedTo(ChronoUnit.DAYS).minusDays(Long.parseLong(numDaysRecoverNotNotified)).toInstant().toEpochMilli();

        String query = String.format("SELECT * FROM c WHERE (c.status = '%s' AND c.generated_at >= %s AND ( %s - c.generated_at) >= %s)",
                ReceiptStatusType.GENERATED, daysAgo, now, millisNotifyDif);

        return executePagedQuery(containerId, query, Receipt.class, continuationToken, pageSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<FeedResponse<Receipt>> getIOErrorToNotifyReceiptDocuments(String continuationToken, Integer pageSize)  {
        long daysAgo = OffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(Long.parseLong(numDaysRecoverNotNotified)).toInstant().toEpochMilli();

        String query = String.format("SELECT * FROM c WHERE c.status = '%s' AND c.generated_at >= %s",
                ReceiptStatusType.IO_ERROR_TO_NOTIFY, daysAgo);

        return executePagedQuery(containerId, query, Receipt.class, continuationToken, pageSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<FeedResponse<Receipt>> getInsertedReceiptDocuments(String continuationToken, Integer pageSize) {
        OffsetDateTime currentDateTime = OffsetDateTime.now();
        long now = currentDateTime.toInstant().toEpochMilli();
        long daysAgo = currentDateTime.truncatedTo(ChronoUnit.DAYS).minusDays(Long.parseLong(numDaysRecoverFailed)).toInstant().toEpochMilli();

        String query = String.format(
                "SELECT * FROM c WHERE (c.status = '%s' AND c.inserted_at >= %s AND ( %s - c.inserted_at) >= %s)",
                ReceiptStatusType.INSERTED, daysAgo, now, millisDiff);

        return executePagedQuery(containerId, query, Receipt.class, continuationToken, pageSize);
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

    private <T> Iterable<FeedResponse<T>> executePagedQuery(String containerName, String query, Class<T> classType, String continuationToken, Integer pageSize) {
        CosmosDatabase cosmosDatabase = this.cosmosClient.getDatabase(databaseId);
        return cosmosDatabase.getContainer(containerName)
                .queryItems(query, new CosmosQueryRequestOptions(), classType)
                .iterableByPage(continuationToken, pageSize);
    }

}
