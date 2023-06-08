package it.gov.pagopa.receipt.pdf.datastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.entities.event.*;
import it.gov.pagopa.receipt.pdf.datastore.entities.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entities.receipt.Receipt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private ArgumentCaptor<List<Receipt>> captor;

    private BizEvent generateValidBizEvent(){
        BizEvent item = new BizEvent();

        Payer payer = new Payer();
        payer.setEntityUniqueIdentifierValue(PAYER_FISCAL_CODE);
        Debtor debtor = new Debtor();
        debtor.setEntityUniqueIdentifierValue(DEBTOR_FISCAL_CODE);

        TransactionDetails transactionDetails = new TransactionDetails();
        Transaction transaction = new Transaction();
        transaction.setCreationDate(String.valueOf(Instant.now()));
        transactionDetails.setTransaction(transaction);

        item.setEventStatus(BizEventStatusType.DONE);
        item.setId(EVENT_ID);
        item.setPayer(payer);
        item.setDebtor(debtor);
        item.setTransactionDetails(transactionDetails);

        return item;
    }

    @Test
    void runOk() {
        // test precondition
        Logger logger = Logger.getLogger("BizEventToReceipt-test-logger");
        when(context.getLogger()).thenReturn(logger);

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent());

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);
        @SuppressWarnings("uncheked")
        OutputBinding<String> queueMessage = (OutputBinding<String>) mock(OutputBinding.class);

        // test execution
        assertDoesNotThrow(() -> function.processBizEventToReceipt(bizEventItems, documentdb, queueMessage, context));

        List<Receipt> argumentList = new ArrayList<>();

        verify(documentdb).setValue(captor.capture());
        Receipt captured = captor.getValue().get(0);
        assertEquals(EVENT_ID, captured.getIdEvent());
        assertEquals(PAYER_FISCAL_CODE, captured.getEventData().getPayerFiscalCode());
        assertEquals(DEBTOR_FISCAL_CODE, captured.getEventData().getDebtorFiscalCode());
    }
}
