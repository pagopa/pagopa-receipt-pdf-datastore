package it.gov.pagopa.receipt.pdf.datastore.helpdesk.http;

import com.microsoft.azure.functions.*;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.ReceiptCosmosServiceImpl;
import it.gov.pagopa.receipt.pdf.datastore.utils.HttpResponseMessageMock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceiptToReviewedTest {

    private static final String BIZ_EVENT_ID = "valid_biz_event_id";
    private final ExecutionContext executionContextMock = mock(ExecutionContext.class);
    private ReceiptToReviewed function;
    @Mock
    private ReceiptCosmosServiceImpl receiptCosmosService;
    @Captor
    private ArgumentCaptor<ReceiptError> receiptErrorCaptor;
    @Mock
    HttpRequestMessage<Optional<String>> request;
    @SuppressWarnings("unchecked")
    OutputBinding<ReceiptError> documentdb = (OutputBinding<ReceiptError>) spy(OutputBinding.class);

    @Test
    void requestWithValidBizEventSaveReceiptErrorInReviewed() throws ReceiptNotFoundException {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        ReceiptError receiptError = ReceiptError.builder()
                .bizEventId(BIZ_EVENT_ID)
                .status(ReceiptErrorStatusType.TO_REVIEW)
                .build();
        when(receiptCosmosService.getReceiptError(BIZ_EVENT_ID)).thenReturn(receiptError);

        function =  spy(new ReceiptToReviewed(receiptCosmosService));

        // test execution
        AtomicReference<HttpResponseMessage> responseMessage = new AtomicReference<>();
        assertDoesNotThrow(() -> responseMessage.set(function.run(request, BIZ_EVENT_ID, documentdb,executionContextMock )));
        assertEquals(HttpStatus.OK, responseMessage.get().getStatus());

        verify(documentdb).setValue(receiptErrorCaptor.capture());
        ReceiptError captured = receiptErrorCaptor.getValue();
        assertEquals(BIZ_EVENT_ID, captured.getBizEventId());
        assertEquals(ReceiptErrorStatusType.REVIEWED, captured.getStatus());
    }

    @Test
    void requestWithValidBizEventIdButReceiptNotFound() throws ReceiptNotFoundException {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        when(receiptCosmosService.getReceiptError(BIZ_EVENT_ID)).thenThrow(ReceiptNotFoundException.class);

        function = spy(new ReceiptToReviewed(receiptCosmosService));

        // test execution
        AtomicReference<HttpResponseMessage> responseMessage = new AtomicReference<>();
        assertDoesNotThrow(() -> responseMessage.set(function.run(request, BIZ_EVENT_ID, documentdb, executionContextMock)));
        assertEquals(HttpStatus.NOT_FOUND, responseMessage.get().getStatus());

        verifyNoInteractions(documentdb);
    }

    @Test
    void requestWithValidBizEventIdButReceiptWrongStatusReturnsInternalServerError() throws ReceiptNotFoundException {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        when(receiptCosmosService.getReceiptError(BIZ_EVENT_ID)).thenReturn(ReceiptError.builder()
                .bizEventId(BIZ_EVENT_ID)
                .status(ReceiptErrorStatusType.REQUEUED)
                .build());

        function = spy(new ReceiptToReviewed(receiptCosmosService));

        // test execution
        AtomicReference<HttpResponseMessage> responseMessage = new AtomicReference<>();
        assertDoesNotThrow(() -> responseMessage.set(function.run(request, BIZ_EVENT_ID, documentdb, executionContextMock)));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, responseMessage.get().getStatus());

        verifyNoInteractions(documentdb);
    }

    @Test
    void requestWithoutEventIdReturnsBadRequest() {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        function = spy(new ReceiptToReviewed(receiptCosmosService));

        // test execution
        AtomicReference<HttpResponseMessage> responseMessage = new AtomicReference<>();
        assertDoesNotThrow(() -> responseMessage.set(function.run(request, null, documentdb, executionContextMock)));
        assertEquals(HttpStatus.BAD_REQUEST, responseMessage.get().getStatus());

        verifyNoInteractions(documentdb);
    }

    private CartForReceipt generateCart() {
        CartForReceipt cart = new CartForReceipt();
        cart.setId("1");
        cart.setStatus(CartStatusType.FAILED);
        cart.setTotalNotice(1);
        cart.setCartPaymentId(new HashSet<>(new ArrayList<>(
                List.of(new String[]{"valid_biz_event_id"}))));
        return cart;
    }
}