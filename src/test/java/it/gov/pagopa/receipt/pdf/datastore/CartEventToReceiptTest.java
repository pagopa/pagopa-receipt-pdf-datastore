package it.gov.pagopa.receipt.pdf.datastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.*;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.service.BizEventToReceiptService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartEventToReceiptTest {

    private static final ReasonError REASON_ERROR = ReasonError.builder().code(500).message("error").build();

    @Mock
    private BizEventToReceiptService bizEventToReceiptServiceMock;

    @Captor
    private ArgumentCaptor<Receipt> receiptCaptor;

    @Captor
    private ArgumentCaptor<CartForReceipt> cartCaptor;

    @Spy
    private OutputBinding<Receipt> receiptDocumentdb;

    @Spy
    private OutputBinding<CartForReceipt> cartForReceiptDocumentdb;

    @Mock
    private ExecutionContext contextMock;

    private AutoCloseable closeable;

    private CartEventToReceipt sut;

    @BeforeEach
    public void openMocks() {
        closeable = MockitoAnnotations.openMocks(this);
        sut = spy(new CartEventToReceipt(bizEventToReceiptServiceMock));
    }

    @AfterEach
    public void releaseMocks() throws Exception {
        closeable.close();
    }

    @Test
    void cartEventToReceiptSuccess() {
        CartForReceipt cartForReceipt = getCartForReceipt();

        when(bizEventToReceiptServiceMock.getCartBizEvents(anyLong())).thenReturn(Collections.singletonList(generateValidBizEvent()));

        assertDoesNotThrow(() -> sut.run(cartForReceipt, receiptDocumentdb, cartForReceiptDocumentdb, contextMock));

        verify(bizEventToReceiptServiceMock).getCartBizEvents(anyLong());
        verify(bizEventToReceiptServiceMock).handleSaveReceipt(any());
        verify(bizEventToReceiptServiceMock).handleSendMessageToQueue(anyList(), any());

        verify(receiptDocumentdb, never()).setValue(receiptCaptor.capture());
        verify(cartForReceiptDocumentdb).setValue(cartCaptor.capture());

        assertEquals(CartStatusType.SENT, cartCaptor.getValue().getStatus());
    }

    @Test
    void cartEventToReceiptSkip() {
        Set<String> bizEventIds = new HashSet<>();
        bizEventIds.add("id");
        CartForReceipt cartForReceipt = CartForReceipt.builder()
                .id(123L)
                .totalNotice(2)
                .cartPaymentId(bizEventIds)
                .build();

        assertDoesNotThrow(() -> sut.run(cartForReceipt, receiptDocumentdb, cartForReceiptDocumentdb, contextMock));

        verify(bizEventToReceiptServiceMock, never()).getCartBizEvents(anyLong());
        verify(bizEventToReceiptServiceMock, never()).handleSaveReceipt(any());
        verify(bizEventToReceiptServiceMock, never()).handleSendMessageToQueue(anyList(), any());

        verify(receiptDocumentdb, never()).setValue(receiptCaptor.capture());
        verify(cartForReceiptDocumentdb, never()).setValue(cartCaptor.capture());
    }

    @Test
    void cartEventToReceiptFailToCreateReceipt() {
        CartForReceipt cartForReceipt = getCartForReceipt();

        Receipt receipt = new Receipt();
        receipt.setStatus(ReceiptStatusType.FAILED);
        receipt.setReasonErr(REASON_ERROR);

        when(bizEventToReceiptServiceMock.getCartBizEvents(anyLong())).thenReturn(Collections.singletonList(new BizEvent()));

        assertDoesNotThrow(() -> sut.run(cartForReceipt, receiptDocumentdb, cartForReceiptDocumentdb, contextMock));

        verify(bizEventToReceiptServiceMock).getCartBizEvents(anyLong());
        verify(bizEventToReceiptServiceMock, never()).handleSaveReceipt(any());
        verify(bizEventToReceiptServiceMock, never()).handleSendMessageToQueue(anyList(), any());

        verify(receiptDocumentdb, never()).setValue(receiptCaptor.capture());
        verify(cartForReceiptDocumentdb).setValue(cartCaptor.capture());

        assertEquals(CartStatusType.FAILED, cartCaptor.getValue().getStatus());
        assertEquals(500, cartCaptor.getValue().getReasonError().getCode());
    }

    @Test
    void cartEventToReceiptFailToSaveReceipt() {
        CartForReceipt cartForReceipt = getCartForReceipt();

        when(bizEventToReceiptServiceMock.getCartBizEvents(anyLong())).thenReturn(Collections.singletonList(generateValidBizEvent()));

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            Receipt receipt = (Receipt) args[0];
            receipt.setStatus(ReceiptStatusType.FAILED);
            receipt.setReasonErr(REASON_ERROR);
            return null; // void method in a block-style lambda, so return null
        }).when(bizEventToReceiptServiceMock).handleSaveReceipt(any());

        assertDoesNotThrow(() -> sut.run(cartForReceipt, receiptDocumentdb, cartForReceiptDocumentdb, contextMock));

        verify(bizEventToReceiptServiceMock).getCartBizEvents(anyLong());
        verify(bizEventToReceiptServiceMock).handleSaveReceipt(any());
        verify(bizEventToReceiptServiceMock, never()).handleSendMessageToQueue(anyList(), any());

        verify(receiptDocumentdb, never()).setValue(receiptCaptor.capture());
        verify(cartForReceiptDocumentdb).setValue(cartCaptor.capture());

        assertEquals(CartStatusType.FAILED, cartCaptor.getValue().getStatus());
        assertEquals(REASON_ERROR, cartCaptor.getValue().getReasonError());
    }

    @Test
    void cartEventToReceiptFailToSendBizEventsOnQueue() {
        CartForReceipt cartForReceipt = getCartForReceipt();

        when(bizEventToReceiptServiceMock.getCartBizEvents(anyLong())).thenReturn(Collections.singletonList(generateValidBizEvent()));

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            Receipt receipt = (Receipt) args[1];
            receipt.setStatus(ReceiptStatusType.FAILED);
            receipt.setReasonErr(REASON_ERROR);
            return null; // void method in a block-style lambda, so return null
        }).when(bizEventToReceiptServiceMock).handleSendMessageToQueue(anyList(), any());

        assertDoesNotThrow(() -> sut.run(cartForReceipt, receiptDocumentdb, cartForReceiptDocumentdb, contextMock));

        verify(bizEventToReceiptServiceMock).getCartBizEvents(anyLong());
        verify(bizEventToReceiptServiceMock).handleSaveReceipt(any());
        verify(bizEventToReceiptServiceMock).handleSendMessageToQueue(anyList(), any());

        verify(receiptDocumentdb).setValue(receiptCaptor.capture());
        assertEquals(ReceiptStatusType.FAILED, receiptCaptor.getValue().getStatus());
        assertEquals(REASON_ERROR, receiptCaptor.getValue().getReasonErr());

        verify(cartForReceiptDocumentdb).setValue(cartCaptor.capture());
        assertEquals(CartStatusType.SENT, cartCaptor.getValue().getStatus());
    }

    @Test
    void cartEventToReceiptSuccessWithRealCreateMethod() {
        CartForReceipt cartForReceipt = getCartForReceipt();

        when(bizEventToReceiptServiceMock.getCartBizEvents(anyLong())).thenReturn(Collections.singletonList(generateValidBizEvent()));

        assertDoesNotThrow(() -> sut.run(cartForReceipt, receiptDocumentdb, cartForReceiptDocumentdb, contextMock));

        verify(bizEventToReceiptServiceMock).getCartBizEvents(anyLong());
        verify(bizEventToReceiptServiceMock).handleSaveReceipt(any());
        verify(bizEventToReceiptServiceMock).handleSendMessageToQueue(anyList(), any());

        verify(receiptDocumentdb, never()).setValue(receiptCaptor.capture());
        verify(cartForReceiptDocumentdb).setValue(cartCaptor.capture());

        assertEquals(CartStatusType.SENT, cartCaptor.getValue().getStatus());
    }

    private CartForReceipt getCartForReceipt() {
        Set<String> bizEventIds = new HashSet<>();
        bizEventIds.add("id");
        bizEventIds.add("id2");
        return CartForReceipt.builder()
                .id(123L)
                .totalNotice(2)
                .cartPaymentId(bizEventIds)
                .build();
    }

    private BizEvent generateValidBizEvent(){
        BizEvent item = new BizEvent();

        Payer payer = new Payer();
        payer.setEntityUniqueIdentifierValue("PAYER_FISCAL_CODE");
        Debtor debtor = new Debtor();
        debtor.setEntityUniqueIdentifierValue("DEBTOR_FISCAL_CODE");

        TransactionDetails transactionDetails = new TransactionDetails();
        Transaction transaction = new Transaction();
        transaction.setCreationDate(String.valueOf(LocalDateTime.now()));
        transactionDetails.setTransaction(transaction);

        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setTotalNotice("2");

        item.setEventStatus(BizEventStatusType.DONE);
        item.setId("test1");
        item.setPayer(payer);
        item.setDebtor(debtor);
        item.setTransactionDetails(transactionDetails);
        item.setPaymentInfo(paymentInfo);

        return item;
    }
}