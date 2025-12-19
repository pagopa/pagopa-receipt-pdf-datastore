package it.gov.pagopa.receipt.pdf.datastore.helpdesk.http;

import com.azure.cosmos.models.FeedResponse;
import com.microsoft.azure.functions.*;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecoverNotNotifiedReceiptMassiveTest {

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

    private RecoverNotNotifiedReceiptMassive sut;

    private AutoCloseable closeable;

    @BeforeEach
    void openMocks() {
        closeable = MockitoAnnotations.openMocks(this);
        sut = spy(new RecoverNotNotifiedReceiptMassive(receiptCosmosServiceMock));
    }

    @AfterEach
    void releaseMocks() throws Exception {
        closeable.close();
    }

    @Test
    void recoverNotNotifiedReceiptMassiveForIOErrorToNotifySuccess() {
        when(requestMock.getQueryParameters())
                .thenReturn(Collections.singletonMap("status", ReceiptStatusType.IO_ERROR_TO_NOTIFY.name()));

        FeedResponse feedResponseMock = mock(FeedResponse.class);
        List<Receipt> receiptList = getReceiptList(ReceiptStatusType.IO_ERROR_TO_NOTIFY);
        when(feedResponseMock.getResults()).thenReturn(receiptList);
        when(receiptCosmosServiceMock.getNotNotifiedReceiptByStatus(any(), any(), any()))
                .thenReturn(Collections.singletonList(feedResponseMock));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentReceipts).setValue(receiptCaptor.capture());

        assertEquals(receiptList.size(), receiptCaptor.getValue().size());
        Receipt captured1 = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.GENERATED, captured1.getStatus());
        assertEquals(EVENT_ID, captured1.getEventId());
        assertEquals(0, captured1.getNotificationNumRetry());
        assertNull(captured1.getReasonErr());
        assertNull(captured1.getReasonErrPayer());
        Receipt captured2 = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.GENERATED, captured2.getStatus());
        assertEquals(EVENT_ID, captured2.getEventId());
        assertEquals(0, captured2.getNotificationNumRetry());
        assertNull(captured2.getReasonErr());
        assertNull(captured2.getReasonErrPayer());
    }

    @Test
    void recoverNotNotifiedReceiptMassiveForGeneratedSuccess() {
        when(requestMock.getQueryParameters())
                .thenReturn(Collections.singletonMap("status", ReceiptStatusType.GENERATED.name()));

        FeedResponse feedResponseMock = mock(FeedResponse.class);
        List<Receipt> receiptList = getReceiptList(ReceiptStatusType.GENERATED);
        when(feedResponseMock.getResults()).thenReturn(receiptList);
        when(receiptCosmosServiceMock.getNotNotifiedReceiptByStatus(any(), any(), any()))
                .thenReturn(Collections.singletonList(feedResponseMock));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentReceipts).setValue(receiptCaptor.capture());

        assertEquals(receiptList.size(), receiptCaptor.getValue().size());
        Receipt captured1 = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.GENERATED, captured1.getStatus());
        assertEquals(EVENT_ID, captured1.getEventId());
        assertEquals(0, captured1.getNotificationNumRetry());
        assertNull(captured1.getReasonErr());
        assertNull(captured1.getReasonErrPayer());
        Receipt captured2 = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.GENERATED, captured2.getStatus());
        assertEquals(EVENT_ID, captured2.getEventId());
        assertEquals(0, captured2.getNotificationNumRetry());
        assertNull(captured2.getReasonErr());
        assertNull(captured2.getReasonErrPayer());
    }

    @Test
    void recoverNotNotifiedReceiptMassiveSuccessWithNoReceiptUpdated() {
        when(requestMock.getQueryParameters())
                .thenReturn(Collections.singletonMap("status", ReceiptStatusType.IO_ERROR_TO_NOTIFY.name()));

        FeedResponse feedResponseMock = mock(FeedResponse.class);
        when(feedResponseMock.getResults()).thenReturn(Collections.emptyList());
        when(receiptCosmosServiceMock.getNotNotifiedReceiptByStatus(any(), any(), any()))
                .thenReturn(Collections.singletonList(feedResponseMock));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentReceipts, never()).setValue(receiptCaptor.capture());
    }

    @Test
    void recoverReceiptFailMissingQueryParam() {
        when(requestMock.getQueryParameters()).thenReturn(Collections.emptyMap());

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, documentReceipts, executionContextMock);

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
    void recoverReceiptFailInvalidStatusType() {
        when(requestMock.getQueryParameters())
                .thenReturn(Collections.singletonMap("status", "INVALID_STATUS"));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, documentReceipts, executionContextMock);

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
    void recoverReceiptFailInvalidRestoreStatusRequested() {
        when(requestMock.getQueryParameters())
                .thenReturn(Collections.singletonMap("status", ReceiptStatusType.FAILED.name()));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = sut.run(requestMock, documentReceipts, executionContextMock);

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



    private Receipt buildReceipt(ReceiptStatusType statusType) {
        return Receipt.builder()
                .eventId(EVENT_ID)
                .status(statusType)
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

    private List<Receipt> getReceiptList(ReceiptStatusType statusType) {
        List<Receipt> receiptList = new ArrayList<>();
        Receipt receipt1 = buildReceipt(statusType);
        Receipt receipt2 = buildReceipt(statusType);
        receiptList.add(receipt1);
        receiptList.add(receipt2);
        return receiptList;
    }
}