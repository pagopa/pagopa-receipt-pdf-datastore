package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

@ExtendWith(MockitoExtension.class)
class ReceiptCosmosClientImplTest {

    public static final String RECEIPT_ID = "a valid receipt id";
    public static final String IO_MESSAGE_ID = "id";

    @Mock
    private CosmosClient cosmosClientMock;
    @Mock
    private CosmosDatabase mockDatabase;
    @Mock
    private CosmosContainer mockContainer;
    @Mock
    private CosmosPagedIterable<Receipt> mockIterable;
    @Mock
    private CosmosPagedIterable<ReceiptError> mockReceiptErrorIterable;
    @Mock
    private Iterable<FeedResponse<Receipt>> mockReceiptIterableByPage;
    @Mock
    private Stream<Receipt> mockReceiptStream;
    @Mock
    private Stream<ReceiptError> mockReceiptErrorStream;

    @InjectMocks
    private ReceiptCosmosClientImpl sut;

    @Test
    void testSingletonConnectionError() throws Exception {
        String mockKey = "mockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeyMK==";
        withEnvironmentVariables(
                "COSMOS_RECEIPT_KEY", mockKey,
                "COSMOS_RECEIPT_SERVICE_ENDPOINT", "",
                "COSMOS_RECEIPT_READ_REGION", ""
        ).execute(() -> Assertions.assertThrows(IllegalArgumentException.class, ReceiptCosmosClientImpl::getInstance)
        );
    }

    @Test
    void getReceiptDocumentOk() {
        Receipt receipt = new Receipt();
        receipt.setId(RECEIPT_ID);

        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.queryItems(anyString(), any(), eq(Receipt.class))).thenReturn(mockIterable);
        when(mockIterable.stream()).thenReturn(mockReceiptStream);
        when(mockReceiptStream.findFirst()).thenReturn(Optional.of(receipt));

        Receipt result = assertDoesNotThrow(() -> sut.getReceiptDocument(RECEIPT_ID));

        assertEquals(RECEIPT_ID, result.getId());
    }

    @Test
    void getReceiptDocumentKo() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.queryItems(anyString(), any(), eq(Receipt.class))).thenReturn(mockIterable);
        when(mockIterable.stream()).thenReturn(mockReceiptStream);
        when(mockReceiptStream.findFirst()).thenReturn(Optional.empty());

        assertThrows(ReceiptNotFoundException.class, () -> sut.getReceiptDocument("an invalid receipt id"));
    }

    @Test
    void getReceiptErrorOk() {
        ReceiptError receiptError = new ReceiptError();
        receiptError.setId(RECEIPT_ID);

        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.queryItems(anyString(), any(), eq(ReceiptError.class))).thenReturn(mockReceiptErrorIterable);
        when(mockReceiptErrorIterable.stream()).thenReturn(mockReceiptErrorStream);
        when(mockReceiptErrorStream.findFirst()).thenReturn(Optional.of(receiptError));

        ReceiptError result = assertDoesNotThrow(() -> sut.getReceiptError(RECEIPT_ID));

        assertEquals(RECEIPT_ID, result.getId());
    }

    @Test
    void saveReceiptsSuccess() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);

        assertDoesNotThrow(() -> sut.saveReceipts(new Receipt()));

        verify(mockContainer).createItem(any());
    }

    @Test
    void runOk_FailedQueryClient() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.queryItems(anyString(), any(), eq(Receipt.class))).thenReturn(mockIterable);
        when(mockIterable.iterableByPage(null, 1)).thenReturn(mockReceiptIterableByPage);

        Iterable<FeedResponse<Receipt>> result =
                assertDoesNotThrow(() -> sut.getFailedReceiptDocuments(null, 1));

        assertNotNull(result);
    }

    @Test
    void getGeneratedReceiptDocuments_Success() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.queryItems(anyString(), any(), eq(Receipt.class))).thenReturn(mockIterable);
        when(mockIterable.iterableByPage(null, 1)).thenReturn(mockReceiptIterableByPage);

        Iterable<FeedResponse<Receipt>> result =
                assertDoesNotThrow(() -> sut.getGeneratedReceiptDocuments(null, 1));

        assertNotNull(result);
    }

    @Test
    void getIOErrorToNotifyReceiptDocuments_Success() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.queryItems(anyString(), any(), eq(Receipt.class))).thenReturn(mockIterable);
        when(mockIterable.iterableByPage(null, 1)).thenReturn(mockReceiptIterableByPage);

        Iterable<FeedResponse<Receipt>> result =
                assertDoesNotThrow(() -> sut.getIOErrorToNotifyReceiptDocuments(null, 1));

        assertNotNull(result);
    }

    @Test
    void getInsertedReceiptDocuments_Success() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.queryItems(anyString(), any(), eq(Receipt.class))).thenReturn(mockIterable);
        when(mockIterable.iterableByPage(null, 1)).thenReturn(mockReceiptIterableByPage);

        Iterable<FeedResponse<Receipt>> result =
                assertDoesNotThrow(() -> sut.getInsertedReceiptDocuments(null, 1));

        assertNotNull(result);
    }
}