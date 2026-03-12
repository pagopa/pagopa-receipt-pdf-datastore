package it.gov.pagopa.receipt.pdf.datastore.helpdesk.schedule;

import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.model.MassiveCartRecoverResult;
import it.gov.pagopa.receipt.pdf.datastore.service.HelpdeskService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, SystemStubsExtension.class})
class RecoverNotNotifiedCartReceiptScheduledTest {

    @Mock
    private ExecutionContext contextMock;

    @Mock
    private HelpdeskService helpdeskServiceMock;

    @SystemStub
    private EnvironmentVariables environment;

    private RecoverNotNotifiedCartReceiptScheduled sut;

    @Test
    @SneakyThrows
    void recoverFailedReceiptScheduledSuccess() {
        sut = new RecoverNotNotifiedCartReceiptScheduled(helpdeskServiceMock);

        doReturn(createMassiveRecoverResult()).when(helpdeskServiceMock).massiveRecoverNoNotifiedCart(CartStatusType.IO_ERROR_TO_NOTIFY);
        doReturn(createMassiveRecoverResult()).when(helpdeskServiceMock).massiveRecoverNoNotifiedCart(CartStatusType.GENERATED);

        // test execution
        assertDoesNotThrow(() -> sut.processRecoverNotNotifiedScheduledTrigger("info", contextMock));
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptScheduledSuccessWithoutAction() {
        sut = new RecoverNotNotifiedCartReceiptScheduled(helpdeskServiceMock);

        doReturn(new MassiveCartRecoverResult()).when(helpdeskServiceMock).massiveRecoverNoNotifiedCart(CartStatusType.IO_ERROR_TO_NOTIFY);
        doReturn(new MassiveCartRecoverResult()).when(helpdeskServiceMock).massiveRecoverNoNotifiedCart(CartStatusType.GENERATED);

        // test execution
        assertDoesNotThrow(() -> sut.processRecoverNotNotifiedScheduledTrigger("info", contextMock));
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptScheduledDisabled() {
        environment.set("NOT_NOTIFIED_CART_AUTORECOVER_ENABLED", "false");
        sut = new RecoverNotNotifiedCartReceiptScheduled(helpdeskServiceMock);

        assertEquals("false", System.getenv("NOT_NOTIFIED_CART_AUTORECOVER_ENABLED"));

        // test execution
        assertDoesNotThrow(() -> sut.processRecoverNotNotifiedScheduledTrigger("info", contextMock));

        verify(helpdeskServiceMock, never()).massiveRecoverFailedCart(any(CartStatusType.class));
    }

    private MassiveCartRecoverResult createMassiveRecoverResult() {
        return MassiveCartRecoverResult.builder()
                .successCounter(1)
                .errorCounter(1)
                .build();
    }
}