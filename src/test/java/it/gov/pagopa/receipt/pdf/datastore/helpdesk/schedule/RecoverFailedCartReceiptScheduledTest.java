package it.gov.pagopa.receipt.pdf.datastore.helpdesk.schedule;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.model.MassiveCartRecoverResult;
import it.gov.pagopa.receipt.pdf.datastore.service.HelpdeskService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, SystemStubsExtension.class})
class RecoverFailedCartReceiptScheduledTest {

    @Mock
    private ExecutionContext contextMock;
    @Mock
    private HelpdeskService helpdeskServiceMock;

    @Captor
    private ArgumentCaptor<List<CartForReceipt>> receiptCaptor;

    @Spy
    private OutputBinding<List<CartForReceipt>> documentdb;

    @SystemStub
    private EnvironmentVariables environment;

    private RecoverFailedCartReceiptScheduled sut;

    @Test
    @SneakyThrows
    void recoverFailedCartReceiptScheduledSuccess() {
        sut = new RecoverFailedCartReceiptScheduled(helpdeskServiceMock);

        doReturn(createMassiveRecoverResult()).when(helpdeskServiceMock).massiveRecoverFailedCart(CartStatusType.FAILED);
        doReturn(createMassiveRecoverResult()).when(helpdeskServiceMock).massiveRecoverFailedCart(CartStatusType.INSERTED);

        // test execution
        assertDoesNotThrow(() -> sut.run("info", documentdb, contextMock));

        verify(documentdb).setValue(receiptCaptor.capture());
        assertNotNull(receiptCaptor.getValue());
        assertEquals(2, receiptCaptor.getValue().size());
    }

    @Test
    @SneakyThrows
    void recoverFailedCartReceiptScheduledSuccessWithoutAction() {
        sut = new RecoverFailedCartReceiptScheduled(helpdeskServiceMock);

        doReturn(new MassiveCartRecoverResult()).when(helpdeskServiceMock).massiveRecoverFailedCart(CartStatusType.FAILED);
        doReturn(new MassiveCartRecoverResult()).when(helpdeskServiceMock).massiveRecoverFailedCart(CartStatusType.INSERTED);

        // test execution
        assertDoesNotThrow(() -> sut.run("info", documentdb, contextMock));

        verify(documentdb).setValue(receiptCaptor.capture());
        assertNotNull(receiptCaptor.getValue());
        assertEquals(0, receiptCaptor.getValue().size());
    }

    @Test
    @SneakyThrows
    void recoverFailedCartReceiptScheduledDisabled() {
        environment.set("FAILED_CART_AUTORECOVER_ENABLED", "false");
        sut = new RecoverFailedCartReceiptScheduled(helpdeskServiceMock);

        assertEquals("false", System.getenv("FAILED_CART_AUTORECOVER_ENABLED"));

        // test execution
        assertDoesNotThrow(() -> sut.run("info", documentdb, contextMock));

        verify(documentdb, never()).setValue(any());
        verify(helpdeskServiceMock, never()).massiveRecoverFailedCart(any(CartStatusType.class));
    }

    private MassiveCartRecoverResult createMassiveRecoverResult() {
        return MassiveCartRecoverResult.builder()
                .successCounter(1)
                .errorCounter(1)
                .failedCartList(List.of(new CartForReceipt()))
                .build();
    }
}