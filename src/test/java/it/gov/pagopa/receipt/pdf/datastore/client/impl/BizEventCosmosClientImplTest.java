package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.Iterator;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

class BizEventCosmosClientImplTest {

    @Mock
    private Iterable<FeedResponse<BizEvent>> feedResponseIterable;

    @Test
    void testSingletonConnectionError() throws Exception {
        String mockKey = "mockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeyMK==";
        withEnvironmentVariables(
                "COSMOS_BIZ_EVENT_KEY", mockKey,
                "COSMOS_BIZ_EVENT_SERVICE_ENDPOINT", "")
                .execute(() -> Assertions.assertThrows(IllegalArgumentException.class, BizEventCosmosClientImpl::getInstance));
    }

    @Test
    void getAllBizEventDocumentsSuccess() {
        CosmosClient mockClient = mock(CosmosClient.class);

        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);

        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);

        Iterator<BizEvent> mockIterator = mock(Iterator.class);
        when(mockIterable.iterator()).thenReturn(mockIterator);

        when(mockContainer.queryItems(anyString(), any(), eq(BizEvent.class))).thenReturn(mockIterable);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);

        BizEventCosmosClientImpl client = new BizEventCosmosClientImpl(mockClient);

//        Assertions.assertDoesNotThrow(() -> client.getAllBizEventDocument("",null, 100));
    }

    @Test
    void getBizEventDocumentSuccess() {
        CosmosClient mockClient = mock(CosmosClient.class);

        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);

        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);

        Iterator<BizEvent> mockIterator = mock(Iterator.class);
        BizEvent bizEvent = new BizEvent();

        when(mockIterator.hasNext()).thenReturn(true);
        when(mockIterator.next()).thenReturn(bizEvent);

        when(mockIterable.iterator()).thenReturn(mockIterator);

        when(mockContainer.queryItems(anyString(), any(), eq(BizEvent.class))).thenReturn(mockIterable);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);

        BizEventCosmosClientImpl client = new BizEventCosmosClientImpl(mockClient);

        Assertions.assertDoesNotThrow(() -> client.getBizEventDocument("1"));
    }

    @Test
    void getBizEventDocumentError() {
        CosmosClient mockClient = mock(CosmosClient.class);

        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);

        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);

        Iterator<BizEvent> mockIterator = mock(Iterator.class);
        BizEvent bizEvent = new BizEvent();

        when(mockIterator.hasNext()).thenReturn(false);
        when(mockIterator.next()).thenReturn(bizEvent);

        when(mockIterable.iterator()).thenReturn(mockIterator);

        when(mockContainer.queryItems(anyString(), any(), eq(BizEvent.class))).thenReturn(mockIterable);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);

        BizEventCosmosClientImpl client = new BizEventCosmosClientImpl(mockClient);

        Assertions.assertThrows(BizEventNotFoundException.class, () -> client.getBizEventDocument("1"));
    }

    @Test
    void getAllBizEventDocument_Success() {
        CosmosClient mockClient = mock(CosmosClient.class);

        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);

        CosmosContainer mockContainer = mock(CosmosContainer.class);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);

        Iterator<BizEvent> mockIterator = mock(Iterator.class);
        BizEvent bizEvent = new BizEvent();

        when(mockIterator.hasNext()).thenReturn(false);
        when(mockIterator.next()).thenReturn(bizEvent);

        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);
        when(mockIterable.iterator()).thenReturn(mockIterator);
        when(mockContainer.queryItems(anyString(), any(CosmosQueryRequestOptions.class), eq(BizEvent.class))).thenReturn(mockIterable);

        BizEventCosmosClientImpl client = new BizEventCosmosClientImpl(mockClient);

        Assertions.assertDoesNotThrow(() -> client.getAllBizEventDocument("1", "asdf", 100));

    }
}