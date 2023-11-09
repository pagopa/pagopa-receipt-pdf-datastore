package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

class BizEventCosmosClientImplTest {

    @Test
    void testSingletonConnectionError() throws Exception {
        String mockKey = "mockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeyMK==";
        withEnvironmentVariables(
                "COSMOS_BIZ_EVENT_KEY", mockKey,
                "COSMOS_BIZ_EVENT_SERVICE_ENDPOINT", ""
        ).execute(() -> Assertions.assertThrows(IllegalArgumentException.class, BizEventCosmosClientImpl::getInstance)
        );
    }

    @Test
    void runOk() throws BizEventNotFoundException {
        String BIZ_EVENT_ID = "a valid event id";

        CosmosClient mockClient = mock(CosmosClient.class);

        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);

        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);

        Iterator<BizEvent> mockIterator = mock(Iterator.class);
        BizEvent bizEvent = new BizEvent();
        bizEvent.setId(BIZ_EVENT_ID);

        when(mockIterator.hasNext()).thenReturn(true);
        when(mockIterator.next()).thenReturn(bizEvent);

        when(mockIterable.iterator()).thenReturn(mockIterator);

        when(mockContainer.queryItems(anyString(), any(), eq(BizEvent.class))).thenReturn(
                mockIterable
        );
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);

        BizEventCosmosClientImpl client = new BizEventCosmosClientImpl(mockClient);

        Assertions.assertDoesNotThrow(() -> client.getBizEventDocument(BIZ_EVENT_ID));

        BizEvent bizEventResponse = client.getBizEventDocument(BIZ_EVENT_ID);
        Assertions.assertEquals(BIZ_EVENT_ID, bizEventResponse.getId());
    }

    @Test
    void runKo() {
        CosmosClient mockClient = mock(CosmosClient.class);

        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);

        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);

        Iterator<Receipt> mockIterator = mock(Iterator.class);

        when(mockIterator.hasNext()).thenReturn(false);

        when(mockIterable.iterator()).thenReturn(mockIterator);

        when(mockContainer.queryItems(anyString(), any(), eq(BizEvent.class))).thenReturn(
                mockIterable
        );
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);

        BizEventCosmosClientImpl client = new BizEventCosmosClientImpl(mockClient);

        Assertions.assertThrows(BizEventNotFoundException.class, () -> client.getBizEventDocument("an invalid receipt id"));
    }

}