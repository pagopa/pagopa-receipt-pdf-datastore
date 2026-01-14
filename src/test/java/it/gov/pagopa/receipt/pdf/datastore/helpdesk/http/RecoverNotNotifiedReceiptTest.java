package it.gov.pagopa.receipt.pdf.datastore.helpdesk.http;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.service.HelpdeskService;
import it.gov.pagopa.receipt.pdf.datastore.service.ReceiptCosmosService;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecoverNotNotifiedReceiptTest {

    private static final String EVENT_ID = "eventId";

    @Mock
    private ExecutionContext executionContextMock;
    @Mock
    private ReceiptCosmosService receiptCosmosServiceMock;
    @Mock
    private HelpdeskService helpdeskServiceMock;

    @Mock
    private HttpRequestMessage<Optional<String>> requestMock;

    @Spy
    private OutputBinding<Receipt> documentReceipts;

    @Captor
    private ArgumentCaptor<Receipt> receiptCaptor;

    @InjectMocks
    private RecoverNotNotifiedReceipt sut;


    @BeforeEach
    void setUp() {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));
    }

    @ParameterizedTest
    @EnumSource(value = ReceiptStatusType.class, names = {"GENERATED", "IO_ERROR_TO_NOTIFY"}, mode = EnumSource.Mode.INCLUDE)
    @SneakyThrows
    void recoverNotNotifiedReceiptSuccess(ReceiptStatusType status) {
        Receipt receipt = buildReceipt();
        receipt.setStatus(status);
        doReturn(receipt).when(receiptCosmosServiceMock).getReceipt(EVENT_ID);
        doReturn(new Receipt()).when(helpdeskServiceMock).recoverNoNotifiedReceipt(receipt);

        // test execution
        HttpResponseMessage response = sut.run(requestMock, EVENT_ID, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentReceipts).setValue(receiptCaptor.capture());

        assertNotNull(receiptCaptor.getValue());
    }

    @Test
    @SneakyThrows
    void recoverNotNotifiedReceiptFailReceiptNotFound() {
        doThrow(ReceiptNotFoundException.class).when(receiptCosmosServiceMock).getReceipt(EVENT_ID);

        // test execution
        HttpResponseMessage response = sut.run(requestMock, EVENT_ID, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentReceipts, never()).setValue(receiptCaptor.capture());
        verify(helpdeskServiceMock, never()).recoverNoNotifiedReceipt(any());
    }

    @ParameterizedTest
    @EnumSource(value = ReceiptStatusType.class, names = {"GENERATED", "IO_ERROR_TO_NOTIFY"}, mode = EnumSource.Mode.EXCLUDE)
    @SneakyThrows
    void recoverNotNotifiedReceiptFailReceiptWithUnexpectedStatus(ReceiptStatusType status) {
        Receipt receipt = buildReceipt();
        receipt.setStatus(status);
        doReturn(receipt).when(receiptCosmosServiceMock).getReceipt(EVENT_ID);

        // test execution
        HttpResponseMessage response = sut.run(requestMock, EVENT_ID, documentReceipts, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentReceipts, never()).setValue(receiptCaptor.capture());
        verify(helpdeskServiceMock, never()).recoverNoNotifiedReceipt(any());
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