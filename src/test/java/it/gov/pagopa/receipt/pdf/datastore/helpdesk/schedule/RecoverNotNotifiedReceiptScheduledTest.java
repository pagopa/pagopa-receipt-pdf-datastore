package it.gov.pagopa.receipt.pdf.datastore.helpdesk.schedule;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
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

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Spy
    private OutputBinding<List<Receipt>> documentdb;

    @Captor
    private ArgumentCaptor<List<Receipt>> receiptCaptor;

    @SystemStub
    private EnvironmentVariables environment;

    private RecoverNotNotifiedReceiptScheduled sut;

    @Test
    @SneakyThrows
    void recoverFailedReceiptScheduledSuccess() {
        sut = new RecoverNotNotifiedReceiptScheduled(helpdeskServiceMock);

        doReturn(List.of(new Receipt())).when(helpdeskServiceMock).massiveRecoverNoNotifiedReceipt(ReceiptStatusType.IO_ERROR_TO_NOTIFY);
        doReturn(List.of(new Receipt())).when(helpdeskServiceMock).massiveRecoverNoNotifiedReceipt(ReceiptStatusType.GENERATED);

        // test execution
        assertDoesNotThrow(() -> sut.processRecoverNotNotifiedScheduledTrigger("info", documentdb, contextMock));

        verify(documentdb).setValue(receiptCaptor.capture());
        assertNotNull(receiptCaptor.getValue());
        assertEquals(2, receiptCaptor.getValue().size());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptScheduledSuccessWithoutAction() {
        sut = new RecoverNotNotifiedReceiptScheduled(helpdeskServiceMock);

        doReturn(Collections.emptyList()).when(helpdeskServiceMock).massiveRecoverNoNotifiedReceipt(ReceiptStatusType.IO_ERROR_TO_NOTIFY);
        doReturn(Collections.emptyList()).when(helpdeskServiceMock).massiveRecoverNoNotifiedReceipt(ReceiptStatusType.GENERATED);

        // test execution
        assertDoesNotThrow(() -> sut.processRecoverNotNotifiedScheduledTrigger("info", documentdb, contextMock));

        verify(documentdb).setValue(receiptCaptor.capture());
        assertNotNull(receiptCaptor.getValue());
        assertEquals(0, receiptCaptor.getValue().size());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptScheduledDisabled() {
        environment.set("NOT_NOTIFIED_AUTORECOVER_ENABLED", "false");
        sut = new RecoverNotNotifiedReceiptScheduled(helpdeskServiceMock);

        assertEquals("false", System.getenv("NOT_NOTIFIED_AUTORECOVER_ENABLED"));

        // test execution
        assertDoesNotThrow(() -> sut.processRecoverNotNotifiedScheduledTrigger("info", documentdb, contextMock));

        verify(documentdb, never()).setValue(any());
        verify(helpdeskServiceMock, never()).massiveRecoverFailedReceipt(any(ReceiptStatusType.class));
    }
}