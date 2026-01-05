package it.gov.pagopa.receipt.pdf.datastore.helpdesk.http;

import com.microsoft.azure.functions.*;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.datastore.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.datastore.utils.HttpResponseMessageMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecoverNotNotifiedReceiptTest {

    private static final String EVENT_ID = "eventId";

    private final ExecutionContext executionContextMock = mock(ExecutionContext.class);

    @Mock
    private ReceiptCosmosService receiptCosmosServiceMock;

    @Mock
    private HttpRequestMessage<Optional<String>> requestMock;

    @Spy
    private OutputBinding<List<Receipt>> documentReceipts;

    @Captor
    private ArgumentCaptor<List<Receipt>> receiptCaptor;

    private RecoverNotNotifiedReceipt sut;

    private AutoCloseable closeable;

    @BeforeEach
    void openMocks() {
        closeable = MockitoAnnotations.openMocks(this);
        sut = spy(new RecoverNotNotifiedReceipt(receiptCosmosServiceMock));
    }

    @AfterEach
    void releaseMocks() throws Exception {
        closeable.close();
    }

    @Test
    void recoverNotNotifiedReceiptSuccess() throws ReceiptNotFoundException {
        Receipt receipt = buildReceipt();
        when(receiptCosmosServiceMock.getReceipt(EVENT_ID)).thenReturn(receipt);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, EVENT_ID, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentReceipts).setValue(receiptCaptor.capture());

        assertEquals(1, receiptCaptor.getValue().size());
        Receipt captured = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.GENERATED, captured.getStatus());
        assertEquals(EVENT_ID, captured.getEventId());
        assertEquals(0, captured.getNotificationNumRetry());
        assertNull(captured.getReasonErr());
        assertNull(captured.getReasonErrPayer());
    }

    @Test
    void recoverReceiptFailForMissingEventId() {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, "", documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());

        ProblemJson problemJson = (ProblemJson) response.getBody();
        assertNotNull(problemJson);
        assertEquals(HttpStatus.BAD_REQUEST.value(), problemJson.getStatus());
        assertEquals(HttpStatus.BAD_REQUEST.name(), problemJson.getTitle());
        assertNotNull(problemJson.getDetail());

        verify(documentReceipts, never()).setValue(receiptCaptor.capture());
    }

    @Test
    void recoverReceiptFailReceiptNotFound() throws ReceiptNotFoundException {
        when(receiptCosmosServiceMock.getReceipt(EVENT_ID)).thenThrow(ReceiptNotFoundException.class);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, EVENT_ID, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentReceipts, never()).setValue(receiptCaptor.capture());
    }

    @Test
    void recoverReceiptFailReceiptInInsertedButOnlyGenerated() throws ReceiptNotFoundException {
        Receipt receipt = new Receipt();
        receipt.setStatus(ReceiptStatusType.INSERTED);
        when(receiptCosmosServiceMock.getReceipt(EVENT_ID)).thenReturn(receipt);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, EVENT_ID, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());

        ProblemJson problemJson = (ProblemJson) response.getBody();
        assertNotNull(problemJson);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problemJson.getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.name(), problemJson.getTitle());
        assertNotNull(problemJson.getDetail());

        verify(documentReceipts, never()).setValue(receiptCaptor.capture());
    }

    @Test
    void recoverReceiptFailReceiptInInsertedButOnlyIOErrorToNotify() throws ReceiptNotFoundException {
        Receipt receipt = new Receipt();
        receipt.setStatus(ReceiptStatusType.INSERTED);
        when(receiptCosmosServiceMock.getReceipt(EVENT_ID)).thenReturn(receipt);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, EVENT_ID, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());

        ProblemJson problemJson = (ProblemJson) response.getBody();
        assertNotNull(problemJson);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problemJson.getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.name(), problemJson.getTitle());
        assertNotNull(problemJson.getDetail());

        verify(documentReceipts, never()).setValue(receiptCaptor.capture());
    }

    private Receipt buildReceipt() {
        return Receipt.builder()
                .eventId(EVENT_ID)
                .status(ReceiptStatusType.IO_ERROR_TO_NOTIFY)
                .reasonErr(ReasonError.builder()
                        .code(500)
                        .message("error message")
                        .build())
                .reasonErrPayer(ReasonError.builder()
                        .code(500)
                        .message("error message")
                        .build())
                .numRetry(0)
                .notificationNumRetry(6)
                .inserted_at(0)
                .generated_at(0)
                .notified_at(0)
                .build();
    }

}