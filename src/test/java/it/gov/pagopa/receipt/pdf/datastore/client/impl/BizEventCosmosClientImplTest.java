package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

@ExtendWith(MockitoExtension.class)
class BizEventCosmosClientImplTest {

    @Mock
    private CosmosClient cosmosClientMock;

    @Mock
    private CosmosDatabase mockDatabase;
    @Mock
    private CosmosContainer mockContainer;
    @Mock
    private CosmosPagedIterable<BizEvent> mockIterable;
    @Mock
    private CosmosItemResponse<BizEvent> mockResponse;

    @InjectMocks
    private BizEventCosmosClientImpl sut;

    @Test
    void testSingletonConnectionError() throws Exception {
        String mockKey = "mockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeyMK==";
        withEnvironmentVariables(
                "COSMOS_BIZ_EVENT_KEY", mockKey,
                "COSMOS_BIZ_EVENT_SERVICE_ENDPOINT", "",
                "COSMOS_BIZ_EVENT_READ_REGION", "")
                .execute(() -> assertThrows(IllegalArgumentException.class, BizEventCosmosClientImpl::getInstance));
    }

    @Test
    void getAllCartBizEventDocumentsSuccess() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.queryItems(anyString(), any(), eq(BizEvent.class))).thenReturn(mockIterable);
        when(mockIterable.stream()).thenReturn(Stream.of(new BizEvent()));

        List<BizEvent> result = assertDoesNotThrow(() -> sut.getAllCartBizEventDocument(""));

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void getAllCartBizEventDocumentsSuccessQueryResultTruncated() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.queryItems(anyString(), any(), eq(BizEvent.class))).thenReturn(mockIterable);
        when(mockIterable.stream()).thenReturn(Stream.of(new BizEvent(), new BizEvent(), new BizEvent(), new BizEvent(), new BizEvent(), new BizEvent(), new BizEvent()));

        List<BizEvent> result = assertDoesNotThrow(() -> sut.getAllCartBizEventDocument(""));

        assertNotNull(result);
        assertEquals(6, result.size());
    }

    @Test
    void getBizEventDocumentSuccess() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.readItem(anyString(), any(), eq(BizEvent.class))).thenReturn(mockResponse);
        when(mockResponse.getItem()).thenReturn(new BizEvent());

        assertDoesNotThrow(() -> sut.getBizEventDocument("1"));
    }

    @Test
    void getBizEventDocumentError() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.readItem(anyString(), any(), eq(BizEvent.class))).thenThrow(CosmosException.class);

        assertThrows(BizEventNotFoundException.class, () -> sut.getBizEventDocument("1"));
    }
}