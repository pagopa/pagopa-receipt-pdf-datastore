package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.SqlParameter;
import com.azure.cosmos.models.SqlQuerySpec;
import it.gov.pagopa.receipt.pdf.datastore.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotFoundException;

import java.util.List;

/**
 * Client for the CosmosDB database
 */
public class BizEventCosmosClientImpl implements BizEventCosmosClient {

    private static BizEventCosmosClientImpl instance;

    private final CosmosClient cosmosClient;
    private final CosmosContainer bizEventContainer;

    private BizEventCosmosClientImpl() {
        String azureKey = System.getenv("COSMOS_BIZ_EVENT_KEY");
        String serviceEndpoint = System.getenv("COSMOS_BIZ_EVENT_SERVICE_ENDPOINT");
        String readRegion = System.getenv("COSMOS_BIZ_EVENT_READ_REGION");

        this.cosmosClient = new CosmosClientBuilder()
                .endpoint(serviceEndpoint)
                .key(azureKey)
                .preferredRegions(List.of(readRegion))
                .buildClient();

        String containerId = System.getenv("COSMOS_BIZ_EVENT_CONTAINER_NAME");
        String databaseId = System.getenv("COSMOS_BIZ_EVENT_DB_NAME");

        this.bizEventContainer = this.cosmosClient.getDatabase(databaseId).getContainer(containerId);
    }

    /**
     * Test-only constructor. Package-private visibility so it is only reachable from tests
     * in the same package.
     */
    BizEventCosmosClientImpl(CosmosClient cosmosClient, CosmosContainer bizEventContainer) {
        this.cosmosClient = cosmosClient;
        this.bizEventContainer = bizEventContainer;
    }

    public static BizEventCosmosClientImpl getInstance() {
        if (instance == null) {
            instance = new BizEventCosmosClientImpl();
        }
        return instance;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BizEvent getBizEventDocument(String bizEventId) throws BizEventNotFoundException {
        try {
            return bizEventContainer.readItem(bizEventId, new PartitionKey(bizEventId), BizEvent.class).getItem();
        } catch (CosmosException e) {
            throw new BizEventNotFoundException("Document not found in the defined container", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BizEvent> getAllCartBizEventDocument(String transactionId) {
        //Build query
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.transactionDetails.transaction.transactionId = @transactionId",
                List.of(new SqlParameter("@transactionId", transactionId))
        );

        //Query the container
        return bizEventContainer
                .queryItems(querySpec, new CosmosQueryRequestOptions(), BizEvent.class)
                .stream().limit(6)
                .toList();
    }
}
