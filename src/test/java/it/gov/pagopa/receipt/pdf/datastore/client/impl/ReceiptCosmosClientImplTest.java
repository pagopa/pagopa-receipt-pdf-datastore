package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.IOMessage;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.exception.IoMessageNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

class ReceiptCosmosClientImplTest {

    @Test
    void testSingletonConnectionError() throws Exception {
        String mockKey = "mockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeyMK==";
        withEnvironmentVariables(
                "COSMOS_RECEIPT_KEY", mockKey,
                "COSMOS_RECEIPT_SERVICE_ENDPOINT", ""
        ).execute(() -> Assertions.assertThrows(IllegalArgumentException.class, ReceiptCosmosClientImpl::getInstance)
        );
    }

    @Test
    void runOk() throws ReceiptNotFoundException {
        String receiptId = "a valid receipt id";

        CosmosClient mockClient = mock(CosmosClient.class);

        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);

        Iterator<Receipt> mockIterator = mock(Iterator.class);
        Receipt receipt = new Receipt();
        receipt.setId(receiptId);

        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);
        when(mockIterable.stream()).thenAnswer(invocation -> Stream.of(receipt));

        when(mockIterable.iterator()).thenReturn(mockIterator);

        when(mockContainer.queryItems(anyString(), any(), eq(Receipt.class))).thenReturn(mockIterable);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);

        ReceiptCosmosClientImpl client = new ReceiptCosmosClientImpl(mockClient);

        Assertions.assertDoesNotThrow(() -> client.getReceiptDocument(receiptId));

        Receipt receiptResponse = client.getReceiptDocument(receiptId);
        Assertions.assertEquals(receiptId, receiptResponse.getId());
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

        when(mockContainer.queryItems(anyString(), any(), eq(Receipt.class))).thenReturn(
                mockIterable
        );
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);

        ReceiptCosmosClientImpl client = new ReceiptCosmosClientImpl(mockClient);

        Assertions.assertThrows(ReceiptNotFoundException.class, () -> client.getReceiptDocument("an invalid receipt id"));
    }

    @Test
    void runOk_FailedQueryClient() {
        String receiptId = "a valid receipt id";

        CosmosClient mockClient = mock(CosmosClient.class);

        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);

        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);

        Iterator<Receipt> mockIterator = mock(Iterator.class);
        Receipt receipt = new Receipt();
        receipt.setId(receiptId);

        when(mockIterator.hasNext()).thenReturn(true);
        when(mockIterator.next()).thenReturn(receipt);

        when(mockIterable.iterator()).thenReturn(mockIterator);

        when(mockContainer.queryItems(anyString(), any(), eq(Receipt.class))).thenReturn(
                mockIterable
        );
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);

        ReceiptCosmosClientImpl client = new ReceiptCosmosClientImpl(mockClient);

        Assertions.assertDoesNotThrow(() -> client.getFailedReceiptDocuments(null, 100));
    }

    @Test
    void getGeneratedReceiptDocuments_Success() {
        String receiptId = "a valid receipt id";

        CosmosClient mockClient = mock(CosmosClient.class);

        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);

        Iterator<Receipt> mockIterator = mock(Iterator.class);
        Receipt receipt = new Receipt();
        receipt.setId(receiptId);

        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);
        when(mockIterable.stream()).thenAnswer(invocation -> Stream.of(receipt));

        when(mockIterable.iterator()).thenReturn(mockIterator);

        when(mockContainer.queryItems(anyString(), any(), eq(Receipt.class)))
                .thenReturn(mockIterable);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);

        ReceiptCosmosClientImpl client = new ReceiptCosmosClientImpl(mockClient);

        Assertions.assertDoesNotThrow(() -> client.getGeneratedReceiptDocuments("scsdf", 100));
    }

    @Test
    void getIOErrorToNotifyReceiptDocuments_Success() {
        String receiptId = "a valid receipt id";

        CosmosClient mockClient = mock(CosmosClient.class);

        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);

        Iterator<Receipt> mockIterator = mock(Iterator.class);
        Receipt receipt = new Receipt();
        receipt.setId(receiptId);

        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);
        when(mockIterable.stream()).thenAnswer(invocation -> Stream.of(receipt));

        when(mockIterable.iterator()).thenReturn(mockIterator);

        when(mockContainer.queryItems(anyString(), any(), eq(Receipt.class)))
                .thenReturn(mockIterable);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);
        ReceiptCosmosClientImpl client = new ReceiptCosmosClientImpl(mockClient);

        Assertions.assertDoesNotThrow(() -> client.getIOErrorToNotifyReceiptDocuments("scsdf", 100));
    }

    @Test
    void getInsertedReceiptDocuments_Success() {
        String receiptId = "a valid receipt id";

        CosmosClient mockClient = mock(CosmosClient.class);

        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);

        Iterator<Receipt> mockIterator = mock(Iterator.class);
        Receipt receipt = new Receipt();
        receipt.setId(receiptId);

        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);
        when(mockIterable.stream()).thenAnswer(invocation -> Stream.of(receipt));

        when(mockIterable.iterator()).thenReturn(mockIterator);

        when(mockContainer.queryItems(anyString(), any(), eq(Receipt.class)))
                .thenReturn(mockIterable);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);
        ReceiptCosmosClientImpl client = new ReceiptCosmosClientImpl(mockClient);

        Assertions.assertDoesNotThrow(() -> client.getInsertedReceiptDocuments("scsdf", 100));
    }

    @Test
    void getIoMessage_Success() {
        CosmosClient mockClient = mock(CosmosClient.class);

        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);

        Iterator<Receipt> mockIterator = mock(Iterator.class);
        IOMessage ioMessage = new IOMessage();

        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);
        when(mockIterable.stream()).thenAnswer(invocation -> Stream.of(ioMessage));

        when(mockIterable.iterator()).thenReturn(mockIterator);

        when(mockContainer.queryItems(anyString(), any(), eq(IOMessage.class))).thenReturn(mockIterable);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);
        ReceiptCosmosClientImpl client = new ReceiptCosmosClientImpl(mockClient);

        Assertions.assertDoesNotThrow(() -> client.getIoMessage("scsdf"));
    }

    @Test
    void getIoMessage_NotFound() {
        CosmosClient mockClient = mock(CosmosClient.class);

        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);

        Iterator<Receipt> mockIterator = mock(Iterator.class);

        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);
        when(mockIterable.stream()).thenAnswer(invocation -> Stream.empty());

        when(mockIterable.iterator()).thenReturn(mockIterator);

        when(mockContainer.queryItems(anyString(), any(), eq(IOMessage.class))).thenReturn(mockIterable);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);
        ReceiptCosmosClientImpl client = new ReceiptCosmosClientImpl(mockClient);

        Assertions.assertThrows(IoMessageNotFoundException.class, () -> client.getIoMessage("asdf"));
    }

}