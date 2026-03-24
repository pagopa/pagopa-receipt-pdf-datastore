package it.gov.pagopa.receipt.pdf.datastore.helpdesk.schedule;

import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.model.MassiveRecoverResult;
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
class RecoverNotNotifiedReceiptScheduledTest {

    @Mock
    private ExecutionContext contextMock;

    @Mock
    private HelpdeskService helpdeskServiceMock;

    @SystemStub
    private EnvironmentVariables environment;

    private RecoverNotNotifiedReceiptScheduled sut;

    @Test
    @SneakyThrows
    void recoverFailedReceiptScheduledSuccess() {
        sut = new RecoverNotNotifiedReceiptScheduled(helpdeskServiceMock);

        doReturn(createMassiveRecoverResult()).when(helpdeskServiceMock).massiveRecoverNoNotifiedReceipt(ReceiptStatusType.IO_ERROR_TO_NOTIFY);
        doReturn(createMassiveRecoverResult()).when(helpdeskServiceMock).massiveRecoverNoNotifiedReceipt(ReceiptStatusType.GENERATED);

        // test execution
        assertDoesNotThrow(() -> sut.processRecoverNotNotifiedScheduledTrigger("info", contextMock));
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptScheduledSuccessWithoutAction() {
        sut = new RecoverNotNotifiedReceiptScheduled(helpdeskServiceMock);

        doReturn(new MassiveRecoverResult()).when(helpdeskServiceMock).massiveRecoverNoNotifiedReceipt(ReceiptStatusType.IO_ERROR_TO_NOTIFY);
        doReturn(new MassiveRecoverResult()).when(helpdeskServiceMock).massiveRecoverNoNotifiedReceipt(ReceiptStatusType.GENERATED);

        // test execution
        assertDoesNotThrow(() -> sut.processRecoverNotNotifiedScheduledTrigger("info", contextMock));
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptScheduledDisabled() {
        environment.set("NOT_NOTIFIED_AUTORECOVER_ENABLED", "false");
        sut = new RecoverNotNotifiedReceiptScheduled(helpdeskServiceMock);

        assertEquals("false", System.getenv("NOT_NOTIFIED_AUTORECOVER_ENABLED"));

        // test execution
        assertDoesNotThrow(() -> sut.processRecoverNotNotifiedScheduledTrigger("info", contextMock));

        verify(helpdeskServiceMock, never()).massiveRecoverFailedReceipt(any(ReceiptStatusType.class));
    }

    private MassiveRecoverResult createMassiveRecoverResult() {
        return MassiveRecoverResult.builder()
                .successCounter(1)
                .errorCounter(1)
                .build();
    }
}