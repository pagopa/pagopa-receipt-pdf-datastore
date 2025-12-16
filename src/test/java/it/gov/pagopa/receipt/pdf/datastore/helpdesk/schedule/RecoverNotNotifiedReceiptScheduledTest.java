package it.gov.pagopa.receipt.pdf.datastore.helpdesk.schedule;

import com.azure.cosmos.models.FeedResponse;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.service.ReceiptCosmosService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RecoverNotNotifiedReceiptScheduledTest {

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

    private RecoverNotNotifiedReceiptScheduled sut;

    private AutoCloseable closeable;

    @BeforeEach
    public void openMocks() {
        closeable = MockitoAnnotations.openMocks(this);
        sut = spy(new RecoverNotNotifiedReceiptScheduled(receiptCosmosServiceMock));
    }

    @AfterEach
    public void releaseMocks() throws Exception {
        closeable.close();
    }

    @Test
    public void scheduledTriggerShouldReturnAllValidReceiptsProcessed() {
        FeedResponse feedResponseMock = mock(FeedResponse.class);
        List<Receipt> receiptList = getReceiptList(ReceiptStatusType.IO_ERROR_TO_NOTIFY);
        when(feedResponseMock.getResults()).thenReturn(receiptList);
        when(receiptCosmosServiceMock.getNotNotifiedReceiptByStatus(any(), any(), eq(ReceiptStatusType.IO_ERROR_TO_NOTIFY)))
                .thenReturn(Collections.singletonList(feedResponseMock));

        FeedResponse feedResponseGenMock = mock(FeedResponse.class);
        List<Receipt> receiptGenList = getReceiptList(ReceiptStatusType.GENERATED);
        when(feedResponseGenMock.getResults()).thenReturn(receiptGenList);
        when(receiptCosmosServiceMock.getNotNotifiedReceiptByStatus(any(), any(), eq(ReceiptStatusType.GENERATED)))
                .thenReturn(Collections.singletonList(feedResponseGenMock));

        sut.processRecoverNotNotifiedScheduledTrigger("info", documentReceipts, executionContextMock);

        verify(documentReceipts).setValue(receiptCaptor.capture());

        assertEquals(receiptList.size()+receiptGenList.size(), receiptCaptor.getValue().size());
        Receipt captured1 = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.GENERATED, captured1.getStatus());
        assertEquals(EVENT_ID, captured1.getEventId());
        assertEquals(0, captured1.getNotificationNumRetry());
        assertNull(captured1.getReasonErr());
        assertNull(captured1.getReasonErrPayer());
        Receipt captured2 = receiptCaptor.getValue().get(1);
        assertEquals(ReceiptStatusType.GENERATED, captured2.getStatus());
        assertEquals(EVENT_ID, captured2.getEventId());
        assertEquals(0, captured2.getNotificationNumRetry());
        assertNull(captured2.getReasonErr());
        assertNull(captured2.getReasonErrPayer());
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