package it.gov.pagopa.receipt.pdf.datastore.helpdesk.schedule;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.model.MassiveRecoverResult;
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
class RecoverFailedReceiptScheduledTest {

    @Mock
    private ExecutionContext contextMock;
    @Mock
    private HelpdeskService helpdeskServiceMock;

    @Captor
    private ArgumentCaptor<List<Receipt>> receiptCaptor;

    @Spy
    private OutputBinding<List<Receipt>> documentdb;

    @SystemStub
    private EnvironmentVariables environment;

    private RecoverFailedReceiptScheduled sut;

    @Test
    @SneakyThrows
    void recoverFailedReceiptScheduledSuccess() {
        sut = new RecoverFailedReceiptScheduled(helpdeskServiceMock);

        doReturn(createMassiveRecoverResult()).when(helpdeskServiceMock).massiveRecoverFailedReceipt(ReceiptStatusType.FAILED);
        doReturn(createMassiveRecoverResult()).when(helpdeskServiceMock).massiveRecoverFailedReceipt(ReceiptStatusType.INSERTED);

        // test execution
        assertDoesNotThrow(() -> sut.run("info", documentdb, contextMock));

        verify(documentdb).setValue(receiptCaptor.capture());
        assertNotNull(receiptCaptor.getValue());
        assertEquals(2, receiptCaptor.getValue().size());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptScheduledSuccessWithoutAction() {
        sut = new RecoverFailedReceiptScheduled(helpdeskServiceMock);

        doReturn(new MassiveRecoverResult()).when(helpdeskServiceMock).massiveRecoverFailedReceipt(ReceiptStatusType.FAILED);
        doReturn(new MassiveRecoverResult()).when(helpdeskServiceMock).massiveRecoverFailedReceipt(ReceiptStatusType.INSERTED);

        // test execution
        assertDoesNotThrow(() -> sut.run("info", documentdb, contextMock));

        verify(documentdb).setValue(receiptCaptor.capture());
        assertNotNull(receiptCaptor.getValue());
        assertEquals(0, receiptCaptor.getValue().size());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptScheduledDisabled() {
        environment.set("FAILED_AUTORECOVER_ENABLED", "false");
        sut = new RecoverFailedReceiptScheduled(helpdeskServiceMock);

        assertEquals("false", System.getenv("FAILED_AUTORECOVER_ENABLED"));

        // test execution
        assertDoesNotThrow(() -> sut.run("info", documentdb, contextMock));

        verify(documentdb, never()).setValue(any());
        verify(helpdeskServiceMock, never()).massiveRecoverFailedReceipt(any(ReceiptStatusType.class));
    }

    private MassiveRecoverResult createMassiveRecoverResult() {
        return MassiveRecoverResult.builder()
                .successCounter(1)
                .errorCounter(1)
                .failedReceiptList(List.of(new Receipt()))
                .build();
    }
}