package it.gov.pagopa.receipt.pdf.datastore.helpdesk.http;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecoverNotNotifiedCartReceiptTest {

    private static final String CART_ID = "cartId";

    @Mock
    private ExecutionContext executionContextMock;
    @Mock
    private CartReceiptCosmosService cartReceiptCosmosServiceMock;
    @Mock
    private HelpdeskService helpdeskServiceMock;

    @Mock
    private HttpRequestMessage<Optional<String>> requestMock;

    @Spy
    private OutputBinding<CartForReceipt> documentdb;

    @Captor
    private ArgumentCaptor<CartForReceipt> cartCaptor;

    @InjectMocks
    private RecoverNotNotifiedCartReceipt sut;


    @BeforeEach
    void setUp() {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));
    }

    @ParameterizedTest
    @EnumSource(value = CartStatusType.class, names = {"GENERATED", "IO_ERROR_TO_NOTIFY"}, mode = EnumSource.Mode.INCLUDE)
    @SneakyThrows
    void recoverNotNotifiedCartReceiptSuccess(CartStatusType status) {
        CartForReceipt cart = createFailedCart();
        cart.setStatus(status);
        doReturn(cart).when(cartReceiptCosmosServiceMock).getCart(CART_ID);
        doReturn(new CartForReceipt()).when(helpdeskServiceMock).recoverNoNotifiedCart(cart);

        // test execution
        HttpResponseMessage response = sut.run(requestMock, CART_ID, documentdb, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb).setValue(cartCaptor.capture());

        assertNotNull(cartCaptor.getValue());
    }

    @Test
    @SneakyThrows
    void recoverNotNotifiedCartReceiptFailReceiptNotFound() {
        doThrow(CartNotFoundException.class).when(cartReceiptCosmosServiceMock).getCart(CART_ID);

        // test execution
        HttpResponseMessage response = sut.run(requestMock, CART_ID, documentdb, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb, never()).setValue(cartCaptor.capture());
        verify(helpdeskServiceMock, never()).recoverNoNotifiedCart(any());
    }

    @ParameterizedTest
    @EnumSource(value = CartStatusType.class, names = {"GENERATED", "IO_ERROR_TO_NOTIFY"}, mode = EnumSource.Mode.EXCLUDE)
    @SneakyThrows
    void recoverNotNotifiedReceiptFailReceiptWithUnexpectedStatus(CartStatusType status) {
        CartForReceipt cart = createFailedCart();
        cart.setStatus(status);
        doReturn(cart).when(cartReceiptCosmosServiceMock).getCart(CART_ID);

        // test execution
        HttpResponseMessage response = sut.run(requestMock, CART_ID, documentdb, executionContextMock);

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb, never()).setValue(cartCaptor.capture());
        verify(helpdeskServiceMock, never()).recoverNoNotifiedCart(any());
    }

    private CartForReceipt createFailedCart() {
        return CartForReceipt.builder()
                .status(CartStatusType.FAILED)
                .build();
    }
}