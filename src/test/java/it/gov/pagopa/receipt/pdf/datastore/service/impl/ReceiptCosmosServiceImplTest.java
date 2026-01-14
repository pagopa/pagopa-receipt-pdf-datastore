package it.gov.pagopa.receipt.pdf.datastore.service.impl;

import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, SystemStubsExtension.class})
class ReceiptCosmosServiceImplTest {

    private static final String CONTINUATION_TOKEN = "continuationToken";
    private static final int PAGE_SIZE = 100;

    @Mock
    private ReceiptCosmosClientImpl receiptCosmosClient;

    @InjectMocks
    private ReceiptCosmosServiceImpl sut;

    @Test
    void getReceipt_OK() throws ReceiptNotFoundException {
        doReturn(new Receipt()).when(receiptCosmosClient).getReceiptDocument(anyString());

        assertDoesNotThrow(() -> sut.getReceipt(anyString()));
    }

    @Test
    void getReceipt_KO() throws ReceiptNotFoundException {
        doThrow(ReceiptNotFoundException.class).when(receiptCosmosClient).getReceiptDocument(anyString());
        ReceiptNotFoundException e = assertThrows(ReceiptNotFoundException.class, () -> sut.getReceipt(anyString()));

        assertNotNull(e);
    }

    @Test
    void getReceiptError_OK() throws ReceiptNotFoundException {
        doReturn(new ReceiptError()).when(receiptCosmosClient).getReceiptError(anyString());

        assertDoesNotThrow(() -> sut.getReceiptError(anyString()));
    }

    @Test
    void getReceiptError_KO_throwsReceiptNotFound() throws ReceiptNotFoundException {
        doThrow(ReceiptNotFoundException.class).when(receiptCosmosClient).getReceiptError(anyString());
        ReceiptNotFoundException e = assertThrows(ReceiptNotFoundException.class, () -> sut.getReceiptError(anyString()));

        assertNotNull(e);
    }

    @Test
    void getReceiptError_KO_nullReceipt() throws ReceiptNotFoundException {
        doReturn(null).when(receiptCosmosClient).getReceiptError(anyString());
        ReceiptNotFoundException e = assertThrows(ReceiptNotFoundException.class, () -> sut.getReceiptError(anyString()));

        assertNotNull(e);
    }

    @Test
    void getNotNotifiedReceiptByStatus_KO_nullStatus() {
        assertThrows(IllegalArgumentException.class, () -> sut.getNotNotifiedReceiptByStatus(CONTINUATION_TOKEN, PAGE_SIZE, null));
    }

    @Test
    void getNotNotifiedReceiptByStatus_KO_unexpectedStatus() {
        assertThrows(IllegalStateException.class, () -> sut.getNotNotifiedReceiptByStatus(CONTINUATION_TOKEN, PAGE_SIZE, ReceiptStatusType.FAILED));
    }

    @Test
    void getNotNotifiedReceiptByStatus_OK_IOErrorToNotify() {
        @SuppressWarnings("unchecked")
        Iterable<FeedResponse<Receipt>> expectedIterable = mock(Iterable.class);
        when(receiptCosmosClient.getIOErrorToNotifyReceiptDocuments(CONTINUATION_TOKEN, PAGE_SIZE)).thenReturn(expectedIterable);

        Iterable<FeedResponse<Receipt>> actualIterable = sut.getNotNotifiedReceiptByStatus(CONTINUATION_TOKEN, PAGE_SIZE, ReceiptStatusType.IO_ERROR_TO_NOTIFY);

        assertNotNull(actualIterable);
        assertEquals(expectedIterable, actualIterable);
        verify(receiptCosmosClient).getIOErrorToNotifyReceiptDocuments(CONTINUATION_TOKEN, PAGE_SIZE);
        verify(receiptCosmosClient, never()).getGeneratedReceiptDocuments(any(), any());
    }

    @Test
    void getNotNotifiedReceiptByStatus_OK_Generated() {
        @SuppressWarnings("unchecked")
        Iterable<FeedResponse<Receipt>> expectedIterable = mock(Iterable.class);
        when(receiptCosmosClient.getGeneratedReceiptDocuments(CONTINUATION_TOKEN, PAGE_SIZE)).thenReturn(expectedIterable);

        Iterable<FeedResponse<Receipt>> actualIterable = sut.getNotNotifiedReceiptByStatus(CONTINUATION_TOKEN, PAGE_SIZE, ReceiptStatusType.GENERATED);

        assertNotNull(actualIterable);
        assertEquals(expectedIterable, actualIterable);
        verify(receiptCosmosClient).getGeneratedReceiptDocuments(CONTINUATION_TOKEN, PAGE_SIZE);
        verify(receiptCosmosClient, never()).getIOErrorToNotifyReceiptDocuments(any(), any());
    }

    @Test
    void getFailedReceiptByStatus_KO_nullStatus() {
        assertThrows(IllegalArgumentException.class, () -> sut.getFailedReceiptByStatus(CONTINUATION_TOKEN, PAGE_SIZE, null));
    }

    @Test
    void getFailedReceiptByStatus_KO_unexpectedStatus() {
        assertThrows(IllegalStateException.class, () -> sut.getFailedReceiptByStatus(CONTINUATION_TOKEN, PAGE_SIZE, ReceiptStatusType.GENERATED));
    }

    @Test
    void getFailedReceiptByStatus_OK_Failed() {
        @SuppressWarnings("unchecked")
        Iterable<FeedResponse<Receipt>> expectedIterable = mock(Iterable.class);
        when(receiptCosmosClient.getFailedReceiptDocuments(CONTINUATION_TOKEN, PAGE_SIZE)).thenReturn(expectedIterable);

        Iterable<FeedResponse<Receipt>> actualIterable = sut.getFailedReceiptByStatus(CONTINUATION_TOKEN, PAGE_SIZE, ReceiptStatusType.FAILED);

        assertNotNull(actualIterable);
        assertEquals(expectedIterable, actualIterable);
        verify(receiptCosmosClient).getFailedReceiptDocuments(CONTINUATION_TOKEN, PAGE_SIZE);
        verify(receiptCosmosClient, never()).getInsertedReceiptDocuments(any(), any());
    }

    @Test
    void getFailedReceiptByStatus_OK_NotQueueSent() {
        @SuppressWarnings("unchecked")
        Iterable<FeedResponse<Receipt>> expectedIterable = mock(Iterable.class);
        when(receiptCosmosClient.getFailedReceiptDocuments(CONTINUATION_TOKEN, PAGE_SIZE)).thenReturn(expectedIterable);

        Iterable<FeedResponse<Receipt>> actualIterable = sut.getFailedReceiptByStatus(CONTINUATION_TOKEN, PAGE_SIZE, ReceiptStatusType.NOT_QUEUE_SENT);

        assertNotNull(actualIterable);
        assertEquals(expectedIterable, actualIterable);
        verify(receiptCosmosClient).getFailedReceiptDocuments(CONTINUATION_TOKEN, PAGE_SIZE);
        verify(receiptCosmosClient, never()).getInsertedReceiptDocuments(any(), any());
    }

    @Test
    void getFailedReceiptByStatus_OK_Inserted() {
        @SuppressWarnings("unchecked")
        Iterable<FeedResponse<Receipt>> expectedIterable = mock(Iterable.class);
        when(receiptCosmosClient.getInsertedReceiptDocuments(CONTINUATION_TOKEN, PAGE_SIZE)).thenReturn(expectedIterable);

        Iterable<FeedResponse<Receipt>> actualIterable = sut.getFailedReceiptByStatus(CONTINUATION_TOKEN, PAGE_SIZE, ReceiptStatusType.INSERTED);

        assertNotNull(actualIterable);
        assertEquals(expectedIterable, actualIterable);
        verify(receiptCosmosClient).getInsertedReceiptDocuments(CONTINUATION_TOKEN, PAGE_SIZE);
        verify(receiptCosmosClient, never()).getFailedReceiptDocuments(any(), any());
    }
}