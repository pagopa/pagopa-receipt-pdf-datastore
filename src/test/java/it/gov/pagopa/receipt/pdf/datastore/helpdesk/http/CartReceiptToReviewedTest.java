package it.gov.pagopa.receipt.pdf.datastore.helpdesk.http;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.CartReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.service.CartReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.datastore.utils.HttpResponseMessageMock;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartReceiptToReviewedTest {

    private static final String CART_ID = "cart_id";

    @Mock
    private ExecutionContext executionContextMock;
    @Mock
    private CartReceiptCosmosService cartReceiptCosmosServiceMock;
    @Captor
    private ArgumentCaptor<CartReceiptError> cartErrorCaptor;
    @Mock
    private HttpRequestMessage<Optional<String>> request;
    @Spy
    private OutputBinding<CartReceiptError> documentdb;

    @InjectMocks
    private CartReceiptToReviewed function;

    @BeforeEach
    void setUp() {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));
    }

    @Test
    @SneakyThrows
    void requestWithValidBizEventSaveReceiptErrorInReviewed() {
        CartReceiptError receiptError = CartReceiptError.builder()
                .id(CART_ID)
                .status(ReceiptErrorStatusType.TO_REVIEW)
                .build();
        when(cartReceiptCosmosServiceMock.getCartReceiptError(CART_ID)).thenReturn(receiptError);

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> function.run(request, CART_ID, documentdb, executionContextMock));
        assertEquals(HttpStatus.OK, response.getStatus());

        verify(documentdb).setValue(cartErrorCaptor.capture());
        CartReceiptError captured = cartErrorCaptor.getValue();
        assertEquals(CART_ID, captured.getId());
        assertEquals(ReceiptErrorStatusType.REVIEWED, captured.getStatus());
    }

    @Test
    @SneakyThrows
    void requestWithValidBizEventIdButReceiptNotFound() {
        when(cartReceiptCosmosServiceMock.getCartReceiptError(CART_ID)).thenThrow(CartNotFoundException.class);

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> function.run(request, CART_ID, documentdb, executionContextMock));
        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());

        verifyNoInteractions(documentdb);
    }

    @Test
    @SneakyThrows
    void requestWithValidBizEventIdButReceiptWrongStatusReturnsInternalServerError() {
        when(cartReceiptCosmosServiceMock.getCartReceiptError(CART_ID)).thenReturn(CartReceiptError.builder()
                .id(CART_ID)
                .status(ReceiptErrorStatusType.REQUEUED)
                .build());

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> function.run(request, CART_ID, documentdb, executionContextMock));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());

        verifyNoInteractions(documentdb);
    }
}