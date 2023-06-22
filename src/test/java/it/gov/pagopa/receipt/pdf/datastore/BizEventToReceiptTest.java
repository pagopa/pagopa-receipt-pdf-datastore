package it.gov.pagopa.receipt.pdf.datastore;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.*;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptQueueClientImpl;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BizEventToReceiptTest {

    private final String PAYER_FISCAL_CODE = "a valid payer CF";
    private final String DEBTOR_FISCAL_CODE = "a valid debtor CF";
    private final String EVENT_ID = "a valid id";

    @Spy
    private BizEventToReceipt function;

    @Mock
    private ExecutionContext context;

    @Captor
    private ArgumentCaptor<List<Receipt>> receiptCaptor;

    @AfterEach
    public void teardown() throws Exception {
        // reset singleton
        Field instance = ReceiptQueueClientImpl.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    @Test
    void runOk() {
        Logger logger = Logger.getLogger("BizEventToReceipt-test-logger");
        when(context.getLogger()).thenReturn(logger);

        ReceiptQueueClientImpl serviceMock = mock(ReceiptQueueClientImpl.class);
        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.CREATED.value());
        when(serviceMock.sendMessageToQueue(anyString())).thenReturn(response);

        BizEventToReceiptTest.setMock(serviceMock);

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent());

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        // test execution
        assertDoesNotThrow(() -> function.processBizEventToReceipt(bizEventItems, documentdb, context));

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt captured = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.INSERTED, captured.getStatus());
        assertEquals(EVENT_ID, captured.getEventId());
        assertEquals(PAYER_FISCAL_CODE, captured.getEventData().getPayerFiscalCode());
        assertEquals(DEBTOR_FISCAL_CODE, captured.getEventData().getDebtorFiscalCode());
    }

    @Test
    void errorAddingMessageToQueue() {
        Logger logger = Logger.getLogger("BizEventToReceipt-test-logger");
        when(context.getLogger()).thenReturn(logger);

        ReceiptQueueClientImpl serviceMock = mock(ReceiptQueueClientImpl.class);
        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(400);
        when(serviceMock.sendMessageToQueue(anyString())).thenReturn(response);

        BizEventToReceiptTest.setMock(serviceMock);

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent());

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        // test execution
        assertDoesNotThrow(() -> function.processBizEventToReceipt(bizEventItems, documentdb, context));

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt captured = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.NOT_QUEUE_SENT, captured.getStatus());
        assertEquals(ReasonErrorCode.ERROR_QUEUE.getCode(), captured.getReasonErr().getCode());
        assertEquals(EVENT_ID, captured.getEventId());
        assertEquals(PAYER_FISCAL_CODE, captured.getEventData().getPayerFiscalCode());
        assertEquals(DEBTOR_FISCAL_CODE, captured.getEventData().getDebtorFiscalCode());
    }

    @Test
    void errorAddingMessageToQueueThrowException() throws Exception {
        Logger logger = Logger.getLogger("BizEventToReceipt-test-logger");
        when(context.getLogger()).thenReturn(logger);

        ReceiptQueueClientImpl serviceMock = mock(ReceiptQueueClientImpl.class);
        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(400);
        when(serviceMock.sendMessageToQueue(anyString())).thenReturn(response);

        BizEventToReceiptTest.setMock(serviceMock);

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent());

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        // test execution
        withEnvironmentVariable("RECEIPT_QUEUE_TOPIC", "wrong-queue-topic").execute(() ->
                assertDoesNotThrow(() ->
                        function.processBizEventToReceipt(bizEventItems, documentdb, context))
        );

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt captured = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.NOT_QUEUE_SENT, captured.getStatus());
        assertEquals(ReasonErrorCode.ERROR_QUEUE.getCode(), captured.getReasonErr().getCode());
        assertEquals(EVENT_ID, captured.getEventId());
        assertEquals(PAYER_FISCAL_CODE, captured.getEventData().getPayerFiscalCode());
        assertEquals(DEBTOR_FISCAL_CODE, captured.getEventData().getDebtorFiscalCode());
    }

    private static void setMock(ReceiptQueueClientImpl mock) {
        try {
            Field instance = ReceiptQueueClientImpl.class.getDeclaredField("instance");
            instance.setAccessible(true);
            instance.set(instance, mock);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private BizEvent generateValidBizEvent(){
        BizEvent item = new BizEvent();

        Payer payer = new Payer();
        payer.setEntityUniqueIdentifierValue(PAYER_FISCAL_CODE);
        Debtor debtor = new Debtor();
        debtor.setEntityUniqueIdentifierValue(DEBTOR_FISCAL_CODE);

        TransactionDetails transactionDetails = new TransactionDetails();
        Transaction transaction = new Transaction();
        transaction.setCreationDate(String.valueOf(LocalDateTime.now()));
        transactionDetails.setTransaction(transaction);

        item.setEventStatus(BizEventStatusType.DONE);
        item.setId(EVENT_ID);
        item.setPayer(payer);
        item.setDebtor(debtor);
        item.setTransactionDetails(transactionDetails);

        return item;
    }
}
