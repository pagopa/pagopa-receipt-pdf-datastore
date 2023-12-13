package it.gov.pagopa.receipt.pdf.datastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.service.BizEventToReceiptService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartEventToReceiptTest {

    @Mock
    private BizEventToReceiptService bizEventToReceiptServiceMock;

    @Captor
    private ArgumentCaptor<Receipt> receiptCaptor;

    @Spy
    private OutputBinding<Receipt> documentdb;

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
        Set<String> bizEventIds = new HashSet<>();
        bizEventIds.add("id");
        bizEventIds.add("id2");
        CartForReceipt cartForReceipt = CartForReceipt.builder()
                .id(123L)
                .totalNotice(2)
                .cartPaymentId(bizEventIds)
                .build();

        when(bizEventToReceiptServiceMock.getCartBizEvents(anyLong())).thenReturn(Collections.singletonList(new BizEvent()));
        when(bizEventToReceiptServiceMock.createCartReceipt(anyList())).thenReturn(new Receipt());

        assertDoesNotThrow(() -> sut.run(cartForReceipt, documentdb, contextMock));

        verify(bizEventToReceiptServiceMock).getCartBizEvents(anyLong());
        verify(bizEventToReceiptServiceMock).createCartReceipt(anyList());
        verify(bizEventToReceiptServiceMock).handleSaveReceipt(any());
        verify(bizEventToReceiptServiceMock).handleSendMessageToQueue(anyList(), any());

        verify(documentdb, never()).setValue(any());
    }
}