package it.gov.pagopa.receipt.pdf.datastore.helpdesk.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventBadRequestException;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventUnprocessableEntityException;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.service.CartReceiptCosmosService;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecoverFailedCartReceiptTest {

    private static final String CART_ID = "a valid id";

    @Mock
    private ExecutionContext contextMock;
    @Mock
    private CartReceiptCosmosService cartReceiptCosmosServiceMock;
    @Mock
    private HttpRequestMessage<Optional<String>> requestMock;
    @Mock
    private HelpdeskService helpdeskServiceMock;

    @Captor
    private ArgumentCaptor<CartForReceipt> cartCaptor;

    @Spy
    private OutputBinding<CartForReceipt> documentdb;

    @InjectMocks
    private RecoverFailedCartReceipt sut;

    @BeforeEach
    void setup() {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));
    }

    @ParameterizedTest
    @EnumSource(value = CartStatusType.class, names = {"WAITING_FOR_BIZ_EVENT", "INSERTED", "NOT_QUEUE_SENT", "FAILED"}, mode = EnumSource.Mode.INCLUDE)
    @SneakyThrows
    void recoverFailedCartReceiptSuccess(CartStatusType status) {
        CartForReceipt failedReceipt = createFailedCart();
        failedReceipt.setStatus(status);
        CartForReceipt recovered = CartForReceipt.builder().status(CartStatusType.INSERTED).build();

        doReturn(failedReceipt).when(cartReceiptCosmosServiceMock).getCart(CART_ID);
        doReturn(recovered).when(helpdeskServiceMock).recoverFailedCart(failedReceipt);

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, CART_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb, never()).setValue(cartCaptor.capture());
    }

    @Test
    @SneakyThrows
    void recoverFailedCartReceiptFailNoReceiptFound() {
        doThrow(CartNotFoundException.class).when(cartReceiptCosmosServiceMock).getCart(CART_ID);

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, CART_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
        assertNotNull(response.getBody());

        verify(helpdeskServiceMock, never()).recoverFailedCart(any());
        verify(documentdb, never()).setValue(cartCaptor.capture());
    }

    @ParameterizedTest
    @EnumSource(value = CartStatusType.class, names = {"WAITING_FOR_BIZ_EVENT", "INSERTED", "NOT_QUEUE_SENT", "FAILED"}, mode = EnumSource.Mode.EXCLUDE)
    @SneakyThrows
    void recoverFailedCartReceiptFailReceiptWithUnexpectedStatus(CartStatusType status) {
        CartForReceipt failedReceipt = createFailedCart();
        failedReceipt.setStatus(status);

        doReturn(failedReceipt).when(cartReceiptCosmosServiceMock).getCart(CART_ID);

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, CART_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatus());
        assertNotNull(response.getBody());

        verify(helpdeskServiceMock, never()).recoverFailedCart(any());
        verify(documentdb, never()).setValue(cartCaptor.capture());
    }

    @Test
    @SneakyThrows
    void recoverFailedCartReceiptFailBizEvenUnprocessable() {
        CartForReceipt failedCart = createFailedCart();

        doReturn(failedCart).when(cartReceiptCosmosServiceMock).getCart(CART_ID);
        doThrow(BizEventUnprocessableEntityException.class).when(helpdeskServiceMock).recoverFailedCart(failedCart);

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, CART_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb, never()).setValue(cartCaptor.capture());
    }

    @Test
    @SneakyThrows
    void recoverFailedCartReceiptFailBizEvenInvalid() {
        CartForReceipt failedCart = createFailedCart();

        doReturn(failedCart).when(cartReceiptCosmosServiceMock).getCart(CART_ID);
        doThrow(BizEventBadRequestException.class).when(helpdeskServiceMock).recoverFailedCart(failedCart);

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, CART_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb, never()).setValue(cartCaptor.capture());
    }

    @Test
    @SneakyThrows
    void recoverFailedCartReceiptFailPDVTokenizerError() {
        CartForReceipt failedCart = createFailedCart();

        doReturn(failedCart).when(cartReceiptCosmosServiceMock).getCart(CART_ID);
        doThrow(PDVTokenizerException.class).when(helpdeskServiceMock).recoverFailedCart(failedCart);

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, CART_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb, never()).setValue(cartCaptor.capture());
    }

    @Test
    @SneakyThrows
    void recoverFailedCartReceiptFailJsonProcessingError() {
        CartForReceipt failedCart = createFailedCart();

        doReturn(failedCart).when(cartReceiptCosmosServiceMock).getCart(CART_ID);
        doThrow(JsonProcessingException.class).when(helpdeskServiceMock).recoverFailedCart(failedCart);

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, CART_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb, never()).setValue(cartCaptor.capture());
    }

    @Test
    @SneakyThrows
    void recoverFailedCartReceiptFailRecoverError() {
        CartForReceipt failedCart = createFailedCart();
        CartForReceipt recovered = CartForReceipt.builder().status(CartStatusType.FAILED).build();

        doReturn(failedCart).when(cartReceiptCosmosServiceMock).getCart(CART_ID);
        doReturn(recovered).when(helpdeskServiceMock).recoverFailedCart(failedCart);

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, CART_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb).setValue(cartCaptor.capture());
        assertNotNull(cartCaptor.getValue());
    }

    private CartForReceipt createFailedCart() {
        return CartForReceipt.builder()
                .status(CartStatusType.FAILED)
                .build();
    }
}