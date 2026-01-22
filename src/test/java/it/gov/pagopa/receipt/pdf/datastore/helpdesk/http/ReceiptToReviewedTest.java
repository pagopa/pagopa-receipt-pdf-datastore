package it.gov.pagopa.receipt.pdf.datastore.helpdesk.http;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.ReceiptCosmosServiceImpl;
import it.gov.pagopa.receipt.pdf.datastore.utils.HttpResponseMessageMock;
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
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiptToReviewedTest {

    private static final String BIZ_EVENT_ID = "valid_biz_event_id";

    @Mock
    private ExecutionContext executionContextMock;
    @Mock
    private ReceiptCosmosServiceImpl receiptCosmosService;
    @Captor
    private ArgumentCaptor<ReceiptError> receiptErrorCaptor;
    @Mock
    private HttpRequestMessage<Optional<String>> request;
    @Spy
    private OutputBinding<ReceiptError> documentdb;

    @InjectMocks
    private ReceiptToReviewed function;

    @BeforeEach
    void setUp() {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));
    }

    @Test
    void requestWithValidBizEventSaveReceiptErrorInReviewed() throws ReceiptNotFoundException {
        ReceiptError receiptError = ReceiptError.builder()
                .bizEventId(BIZ_EVENT_ID)
                .status(ReceiptErrorStatusType.TO_REVIEW)
                .build();
        when(receiptCosmosService.getReceiptError(BIZ_EVENT_ID)).thenReturn(receiptError);

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> function.run(request, BIZ_EVENT_ID, documentdb, executionContextMock));
        assertEquals(HttpStatus.OK, response.getStatus());

        verify(documentdb).setValue(receiptErrorCaptor.capture());
        ReceiptError captured = receiptErrorCaptor.getValue();
        assertEquals(BIZ_EVENT_ID, captured.getBizEventId());
        assertEquals(ReceiptErrorStatusType.REVIEWED, captured.getStatus());
    }

    @Test
    void requestWithValidBizEventIdButReceiptNotFound() throws ReceiptNotFoundException {
        when(receiptCosmosService.getReceiptError(BIZ_EVENT_ID)).thenThrow(ReceiptNotFoundException.class);

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> function.run(request, BIZ_EVENT_ID, documentdb, executionContextMock));
        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());

        verifyNoInteractions(documentdb);
    }

    @Test
    void requestWithValidBizEventIdButReceiptWrongStatusReturnsInternalServerError() throws ReceiptNotFoundException {
        when(receiptCosmosService.getReceiptError(BIZ_EVENT_ID)).thenReturn(ReceiptError.builder()
                .bizEventId(BIZ_EVENT_ID)
                .status(ReceiptErrorStatusType.REQUEUED)
                .build());

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> function.run(request, BIZ_EVENT_ID, documentdb, executionContextMock));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatus());

        verifyNoInteractions(documentdb);
    }
}