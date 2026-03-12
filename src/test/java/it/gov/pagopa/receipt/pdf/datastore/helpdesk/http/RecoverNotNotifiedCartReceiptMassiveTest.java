package it.gov.pagopa.receipt.pdf.datastore.helpdesk.http;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.model.MassiveCartRecoverResult;
import it.gov.pagopa.receipt.pdf.datastore.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.datastore.service.HelpdeskService;
import it.gov.pagopa.receipt.pdf.datastore.utils.HttpResponseMessageMock;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class RecoverNotNotifiedCartReceiptMassiveTest {

    @Mock
    private ExecutionContext executionContextMock;
    @Mock
    private HelpdeskService helpdeskServiceMock;
    @Mock
    private HttpRequestMessage<Optional<String>> requestMock;

    @InjectMocks
    private RecoverNotNotifiedCartReceiptMassive sut;

    @BeforeEach
    void openMocks() {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));
    }

    @ParameterizedTest
    @EnumSource(value = CartStatusType.class, names = {"GENERATED", "IO_ERROR_TO_NOTIFY"}, mode = EnumSource.Mode.INCLUDE)
    @SneakyThrows
    void recoverNotNotifiedCartReceiptMassiveSuccess(CartStatusType status) {
        doReturn(Collections.singletonMap("status", status.name())).when(requestMock).getQueryParameters();
        doReturn(createMassiveRecoverResult(1, 0)).when(helpdeskServiceMock).massiveRecoverNoNotifiedCart(status);

        // test execution
        HttpResponseMessage response = sut.run(requestMock, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.getBody());
    }

    @ParameterizedTest
    @EnumSource(value = CartStatusType.class, names = {"GENERATED", "IO_ERROR_TO_NOTIFY"}, mode = EnumSource.Mode.INCLUDE)
    @SneakyThrows
    void recoverNotNotifiedCartReceiptMassivePartialSuccess(CartStatusType status) {
        doReturn(Collections.singletonMap("status", status.name())).when(requestMock).getQueryParameters();
        doReturn(createMassiveRecoverResult(1, 1)).when(helpdeskServiceMock).massiveRecoverNoNotifiedCart(status);

        // test execution
        HttpResponseMessage response = sut.run(requestMock, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
        assertNotNull(response.getBody());
        assertEquals(HttpStatus.MULTI_STATUS.value(), ((ProblemJson) response.getBody()).getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = CartStatusType.class, names = {"GENERATED", "IO_ERROR_TO_NOTIFY"}, mode = EnumSource.Mode.INCLUDE)
    @SneakyThrows
    void recoverNotNotifiedCartReceiptMassiveSuccessWithoutAction(CartStatusType status) {
        doReturn(Collections.singletonMap("status", status.name())).when(requestMock).getQueryParameters();
        doReturn(createMassiveRecoverResult(0, 0)).when(helpdeskServiceMock).massiveRecoverNoNotifiedCart(status);

        // test execution
        HttpResponseMessage response = sut.run(requestMock, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.getBody());
    }

    @Test
    @SneakyThrows
    void recoverNotNotifiedCartReceiptMassiveFailParamNull() {
        // test execution
        HttpResponseMessage response = sut.run(requestMock, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());
    }

    @Test
    @SneakyThrows
    void recoverNotNotifiedCartReceiptMassiveFailParamNotAStatus() {
        doReturn(Collections.singletonMap("status", "random")).when(requestMock).getQueryParameters();

        // test execution
        HttpResponseMessage response = sut.run(requestMock, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());
    }

    @ParameterizedTest
    @EnumSource(value = CartStatusType.class, names = {"GENERATED", "IO_ERROR_TO_NOTIFY"}, mode = EnumSource.Mode.EXCLUDE)
    @SneakyThrows
    void recoverNotNotifiedReceiptMassiveFailParamNotAProcessableStatus(CartStatusType status) {
        doReturn(Collections.singletonMap("status", status.name())).when(requestMock).getQueryParameters();

        // test execution
        HttpResponseMessage response = sut.run(requestMock, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatus());
        assertNotNull(response.getBody());
    }

    private MassiveCartRecoverResult createMassiveRecoverResult(int successCounter, int errorCounter) {
        return MassiveCartRecoverResult.builder()
                .successCounter(successCounter)
                .errorCounter(errorCounter)
                .build();
    }
}