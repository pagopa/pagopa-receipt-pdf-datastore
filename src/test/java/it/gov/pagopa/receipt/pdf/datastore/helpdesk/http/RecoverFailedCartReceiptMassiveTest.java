package it.gov.pagopa.receipt.pdf.datastore.helpdesk.http;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.model.MassiveCartRecoverResult;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecoverFailedCartReceiptMassiveTest {

    @Mock
    private ExecutionContext contextMock;
    @Mock
    private HelpdeskService helpdeskServiceMock;

    @Mock
    private HttpRequestMessage<Optional<String>> requestMock;

    @Captor
    private ArgumentCaptor<List<CartForReceipt>> cartCaptor;

    @Spy
    private OutputBinding<List<CartForReceipt>> documentdb;

    @InjectMocks
    private RecoverFailedCartReceiptMassive sut;

    @BeforeEach
    void openMocks() {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));
    }

    @ParameterizedTest
    @EnumSource(value = CartStatusType.class, names = {"INSERTED", "NOT_QUEUE_SENT", "FAILED"}, mode = EnumSource.Mode.INCLUDE)
    @SneakyThrows
    void recoverFailedCartReceiptMassiveSuccess(CartStatusType status) {
        doReturn(Collections.singletonMap("status", status.name())).when(requestMock).getQueryParameters();
        doReturn(new MassiveCartRecoverResult()).when(helpdeskServiceMock).massiveRecoverFailedCart(any(CartStatusType.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb, never()).setValue(cartCaptor.capture());
    }

    @Test
    @SneakyThrows
    void recoverFailedCartReceiptMassiveFailParamNull() {
        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());

        verify(helpdeskServiceMock, never()).massiveRecoverFailedCart(any(CartStatusType.class));
        verify(documentdb, never()).setValue(cartCaptor.capture());
    }

    @Test
    @SneakyThrows
    void recoverFailedCartReceiptMassiveFailParamNotAStatus() {
        doReturn(Collections.singletonMap("status", "random")).when(requestMock).getQueryParameters();

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());

        verify(helpdeskServiceMock, never()).massiveRecoverFailedCart(any(CartStatusType.class));
        verify(documentdb, never()).setValue(cartCaptor.capture());
    }

    @ParameterizedTest
    @EnumSource(value = CartStatusType.class, names = {"INSERTED", "NOT_QUEUE_SENT", "FAILED"}, mode = EnumSource.Mode.EXCLUDE)
    @SneakyThrows
    void recoverFailedCartReceiptMassiveFailStatusParamUnprocessable(CartStatusType status) {
        doReturn(Collections.singletonMap("status", status.name())).when(requestMock).getQueryParameters();

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatus());
        assertNotNull(response.getBody());

        verify(helpdeskServiceMock, never()).massiveRecoverFailedCart(any(CartStatusType.class));
        verify(documentdb, never()).setValue(cartCaptor.capture());
    }

    @Test
    @SneakyThrows
    void recoverFailedCartReceiptMassiveFailRecoverError() {
        MassiveCartRecoverResult recoverResult = MassiveCartRecoverResult.builder()
                .successCounter(1)
                .errorCounter(1)
                .failedCartList(List.of(new CartForReceipt()))
                .build();

        doReturn(Collections.singletonMap("status", CartStatusType.FAILED.name())).when(requestMock).getQueryParameters();
        doReturn(recoverResult).when(helpdeskServiceMock).massiveRecoverFailedCart(any(CartStatusType.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb).setValue(cartCaptor.capture());
        assertNotNull(cartCaptor.getValue());
    }
}