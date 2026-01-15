package it.gov.pagopa.receipt.pdf.datastore.helpdesk.http;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.model.MassiveRecoverResult;
import it.gov.pagopa.receipt.pdf.datastore.service.HelpdeskService;
import it.gov.pagopa.receipt.pdf.datastore.utils.HttpResponseMessageMock;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecoverFailedReceiptMassiveTest {

    @Mock
    private ExecutionContext contextMock;
    @Mock
    private HelpdeskService helpdeskServiceMock;

    @Mock
    private HttpRequestMessage<Optional<String>> requestMock;

    @Captor
    private ArgumentCaptor<List<Receipt>> receiptCaptor;

    @Spy
    private OutputBinding<List<Receipt>> documentdb;

    @InjectMocks
    private RecoverFailedReceiptMassive sut;

    @BeforeEach
    void openMocks() {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));
    }

    @ParameterizedTest
    @EnumSource(value = ReceiptStatusType.class, names = {"INSERTED", "NOT_QUEUE_SENT", "FAILED"}, mode = EnumSource.Mode.INCLUDE)
    @SneakyThrows
    void recoverFailedReceiptMassiveSuccess(ReceiptStatusType status) {
        doReturn(Collections.singletonMap("status", status.name())).when(requestMock).getQueryParameters();
        doReturn(new MassiveRecoverResult()).when(helpdeskServiceMock).massiveRecoverFailedReceipt(any(ReceiptStatusType.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb, never()).setValue(receiptCaptor.capture());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptMassiveFailParamNull() {
        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());

        verify(helpdeskServiceMock, never()).massiveRecoverFailedReceipt(any(ReceiptStatusType.class));
        verify(documentdb, never()).setValue(receiptCaptor.capture());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptMassiveFailParamNotAStatus() {
        doReturn(Collections.singletonMap("status", "random")).when(requestMock).getQueryParameters();

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());

        verify(helpdeskServiceMock, never()).massiveRecoverFailedReceipt(any(ReceiptStatusType.class));
        verify(documentdb, never()).setValue(receiptCaptor.capture());
    }

    @ParameterizedTest
    @EnumSource(value = ReceiptStatusType.class, names = {"INSERTED", "NOT_QUEUE_SENT", "FAILED"}, mode = EnumSource.Mode.EXCLUDE)
    @SneakyThrows
    void recoverFailedReceiptMassiveFailStatusParamUnprocessable(ReceiptStatusType status) {
        doReturn(Collections.singletonMap("status", status.name())).when(requestMock).getQueryParameters();

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatus());
        assertNotNull(response.getBody());

        verify(helpdeskServiceMock, never()).massiveRecoverFailedReceipt(any(ReceiptStatusType.class));
        verify(documentdb, never()).setValue(receiptCaptor.capture());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptMassiveFailRecoverError() {
        MassiveRecoverResult recoverResult = MassiveRecoverResult.builder()
                .successCounter(1)
                .errorCounter(1)
                .failedReceiptList(List.of(new Receipt()))
                .build();

        doReturn(Collections.singletonMap("status", ReceiptStatusType.FAILED.name())).when(requestMock).getQueryParameters();
        doReturn(recoverResult).when(helpdeskServiceMock).massiveRecoverFailedReceipt(any(ReceiptStatusType.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb).setValue(receiptCaptor.capture());
        assertNotNull(receiptCaptor.getValue());
    }
}