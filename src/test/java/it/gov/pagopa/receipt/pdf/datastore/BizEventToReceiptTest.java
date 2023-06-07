package it.gov.pagopa.receipt.pdf.datastore;

import com.microsoft.azure.functions.*;
import it.gov.pagopa.receipt.pdf.datastore.entities.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entities.receipt.Receipt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BizEventToReceiptTest {

    @Spy
    BizEventToReceipt function;

    @Mock
    ExecutionContext context;

    @Test
    void runOk() {
        // test precondition
        Logger logger = Logger.getLogger("example-test-logger");
        when(context.getLogger()).thenReturn(logger);

        final HttpResponseMessage.Builder builder = mock(HttpResponseMessage.Builder.class);

        BizEvent item = mock(BizEvent.class);
        List<BizEvent> items = new ArrayList<BizEvent>(Arrays.asList(item));

        Receipt receipt = mock(Receipt.class);
        List<Receipt> listReceipt = new ArrayList<Receipt>(Arrays.asList(receipt));
        //OutputBinding<List<Receipt>> documentdb = new OutputBinding<List<Receipt>>();

        HttpResponseMessage responseMock = mock(HttpResponseMessage.class);
        doReturn(HttpStatus.OK).when(responseMock).getStatus();
        doReturn(responseMock).when(builder).build();

        // test execution
        //HttpResponseMessage response = function.processBizEventEnrichment(items, OutputBinding<List<Receipt>> documentdb);

        // test assertion
        //assertEquals(HttpStatus.OK, response.getStatus());
    }
}
