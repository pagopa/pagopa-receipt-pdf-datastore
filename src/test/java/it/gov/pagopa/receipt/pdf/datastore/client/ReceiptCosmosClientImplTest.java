package it.gov.pagopa.receipt.pdf.datastore.client;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReceiptCosmosClientImplTest {

    @Test
    void runOk() throws ReceiptNotFoundException {
        String RECEIPT_ID = "a valid receipt id";

        CosmosClient mockClient = mock(CosmosClient.class);

        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);

        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);

        Iterator<Receipt> mockIterator = mock(Iterator.class);
        Receipt receipt = new Receipt();
        receipt.setId(RECEIPT_ID);

        when(mockIterator.hasNext()).thenReturn(true);
        when(mockIterator.next()).thenReturn(receipt);

        when(mockIterable.iterator()).thenReturn(mockIterator);

        when(mockContainer.queryItems(anyString(), any(), eq(Receipt.class))).thenReturn(
                mockIterable
        );
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);

        ReceiptCosmosClientImpl client = new ReceiptCosmosClientImpl(mockClient);

        Assertions.assertDoesNotThrow(() -> client.getReceiptDocument(RECEIPT_ID));

        Receipt receiptResponse = client.getReceiptDocument(RECEIPT_ID);
        Assertions.assertEquals(RECEIPT_ID, receiptResponse.getId());
    }

    @Test
    void runKo() throws ReceiptNotFoundException {
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

        Assertions.assertThrows(ReceiptNotFoundException.class, () -> client.getReceiptDocument("a non-valid receipt id"));
    }

}