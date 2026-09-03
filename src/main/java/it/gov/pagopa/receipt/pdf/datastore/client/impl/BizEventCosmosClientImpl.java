package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import it.gov.pagopa.receipt.pdf.datastore.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.DEP_COSMOS_BIZ_EVENTS;
import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.DETAILS_RESULT_COUNT;
import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.DETAILS_STATUS_CODE;
import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.logIoFailure;
import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.logIoSuccess;

/**
 * Client for the CosmosDB database
 */
public class BizEventCosmosClientImpl implements BizEventCosmosClient {

    private final Logger logger = LoggerFactory.getLogger(BizEventCosmosClientImpl.class);

    private final CosmosContainer bizEventContainer;

    @SuppressWarnings("resource") // CosmosClient lifecycle == singleton lifecycle; never closed on purpose
    private BizEventCosmosClientImpl() {
        String azureKey = System.getenv("COSMOS_BIZ_EVENT_KEY");
        String serviceEndpoint = System.getenv("COSMOS_BIZ_EVENT_SERVICE_ENDPOINT");
        String readRegion = System.getenv("COSMOS_BIZ_EVENT_READ_REGION");

        String databaseId = System.getenv("COSMOS_BIZ_EVENT_DB_NAME");
        String containerId = System.getenv("COSMOS_BIZ_EVENT_CONTAINER_NAME");

        CosmosDatabase database = new CosmosClientBuilder()
                .endpoint(serviceEndpoint)
                .key(azureKey)
                .preferredRegions(List.of(readRegion))
                .buildClient()
                .getDatabase(databaseId);

        this.bizEventContainer = database.getContainer(containerId);
    }

    /**
     * Test-only constructor. Package-private visibility so it is only reachable from tests
     * in the same package.
     */
    BizEventCosmosClientImpl(CosmosContainer bizEventContainer) {
        this.bizEventContainer = bizEventContainer;
    }

    public static BizEventCosmosClientImpl getInstance() {
        return SingletonHelper.INSTANCE;
    }

    /**
     * Bill Pugh singleton holder: the JVM guarantees that the class is loaded
     * (and therefore INSTANCE initialized) lazily and in a thread-safe way.
     */
    private static class SingletonHelper {
        private static final BizEventCosmosClientImpl INSTANCE = new BizEventCosmosClientImpl();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BizEvent getBizEventDocument(String bizEventId) throws BizEventNotFoundException {
        long startNanos = System.nanoTime();
        try {
            BizEvent bizEvent = bizEventContainer.readItem(bizEventId, new PartitionKey(bizEventId), BizEvent.class).getItem();
            logIoSuccess(logger, "Found Biz Event", DEP_COSMOS_BIZ_EVENTS, null, startNanos, null);
            return bizEvent;
        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                logIoSuccess(logger, "Biz Event not found",
                        DEP_COSMOS_BIZ_EVENTS, null, startNanos,
                        Map.of(DETAILS_STATUS_CODE, 404));
                throw new BizEventNotFoundException("Document not found in the defined container", e);
            }
            logIoFailure(logger, "Error fetching Biz Event",
                    DEP_COSMOS_BIZ_EVENTS, null, startNanos, e,
                    Map.of(DETAILS_STATUS_CODE, e.getStatusCode()));
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BizEvent> getAllCartBizEventDocument(String transactionId) {
        long startNanos = System.nanoTime();
        try {
            SqlQuerySpec querySpec = new SqlQuerySpec(
                    "SELECT * FROM c WHERE c.transactionDetails.transaction.transactionId = @transactionId",
                    List.of(new SqlParameter("@transactionId", transactionId))
            );
            List<BizEvent> results = bizEventContainer
                    .queryItems(querySpec, new CosmosQueryRequestOptions(), BizEvent.class)
                    .stream().limit(6)
                    .toList();
            logIoSuccess(logger, "Cart Biz Events fetched",
                    DEP_COSMOS_BIZ_EVENTS, null, startNanos,
                    Map.of(DETAILS_RESULT_COUNT, results.size()));
            return results;
        } catch (RuntimeException e) {
            logIoFailure(logger, "Error fetching cart Biz Events",
                    DEP_COSMOS_BIZ_EVENTS, null, startNanos, e, null);
            throw e;
        }
    }
}
