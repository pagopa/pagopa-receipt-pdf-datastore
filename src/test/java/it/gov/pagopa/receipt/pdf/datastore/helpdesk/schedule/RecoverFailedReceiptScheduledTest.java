package it.gov.pagopa.receipt.pdf.datastore.helpdesk.schedule;

import com.azure.cosmos.models.ModelBridgeInternal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.*;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.CartItem;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.datastore.service.ReceiptCosmosService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.stubbing.Answer;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(SystemStubsExtension.class)
class RecoverFailedReceiptScheduledTest {

    private final String TOKENIZED_DEBTOR_FISCAL_CODE = "tokenizedDebtorFiscalCode";
    private final String TOKENIZED_PAYER_FISCAL_CODE = "tokenizedPayerFiscalCode";
    private final String EVENT_ID_1 = "a valid id 1";
    private final String EVENT_ID_2 = "a valid id 2";
    private final String EVENT_ID_3 = "a valid id 3";

    @Mock
    private ExecutionContext contextMock;
    @Mock
    private ReceiptCosmosService receiptCosmosServiceMock;
    @Mock
    private BizEventCosmosClientImpl bizEventCosmosClientMock;
    @Mock
    private BizEventToReceiptService bizEventToReceiptServiceMock;

    @Captor
    private ArgumentCaptor<List<Receipt>> receiptCaptor;

    @Spy
    private OutputBinding<List<Receipt>> documentdb;

    @SystemStub
    private EnvironmentVariables environment;

    private AutoCloseable closeable;

    private RecoverFailedReceiptScheduled sut;

    @BeforeEach
    void openMocks() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void releaseMocks() throws Exception {
        closeable.close();
    }

    @Test
    void recoverFailedReceiptScheduledSuccess() throws BizEventNotFoundException, PDVTokenizerException, JsonProcessingException {
        sut = spy(new RecoverFailedReceiptScheduled(bizEventToReceiptServiceMock, bizEventCosmosClientMock, receiptCosmosServiceMock));

        when(receiptCosmosServiceMock.getFailedReceiptByStatus(any(), any(), eq(ReceiptStatusType.FAILED)))
                .thenReturn(Collections.singletonList(ModelBridgeInternal
                        .createFeedResponse(Collections.singletonList(
                                        createFailedReceipt(EVENT_ID_1, ReceiptStatusType.FAILED)),
                                Collections.emptyMap())));

        when(receiptCosmosServiceMock.getFailedReceiptByStatus(any(), any(), eq(ReceiptStatusType.INSERTED)))
                .thenReturn(Collections.singletonList(ModelBridgeInternal
                        .createFeedResponse(Collections.singletonList(
                                        createFailedReceipt(EVENT_ID_2, ReceiptStatusType.INSERTED)),
                                Collections.emptyMap())));
        when(receiptCosmosServiceMock.getFailedReceiptByStatus(any(), any(), eq(ReceiptStatusType.NOT_QUEUE_SENT)))
                .thenReturn(Collections.singletonList(ModelBridgeInternal
                        .createFeedResponse(Collections.singletonList(
                                        createFailedReceipt(EVENT_ID_3, ReceiptStatusType.NOT_QUEUE_SENT)),
                                Collections.emptyMap())));

        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID_1))
                .thenReturn(generateValidBizEvent(EVENT_ID_1));
        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID_2))
                .thenReturn(generateValidBizEvent(EVENT_ID_2));
        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID_3))
                .thenReturn(generateValidBizEvent(EVENT_ID_3));

        Answer<Void> successAnswer = invocation -> {
            // arg 0: BizEvent, arg 1: Receipt, arg 2: EventData
            EventData eventDataArg = invocation.getArgument(2);

            // simulate tokenization
            eventDataArg.setPayerFiscalCode(TOKENIZED_PAYER_FISCAL_CODE);
            eventDataArg.setDebtorFiscalCode(TOKENIZED_DEBTOR_FISCAL_CODE);
            return null;
        };

        doAnswer(successAnswer).when(bizEventToReceiptServiceMock).tokenizeFiscalCodes(any(), any(), any());

        // test execution
        assertDoesNotThrow(() -> sut.run("info", documentdb, contextMock));

        verify(receiptCosmosServiceMock).getFailedReceiptByStatus(any(), any(), eq(ReceiptStatusType.INSERTED));
        verify(receiptCosmosServiceMock).getFailedReceiptByStatus(any(), any(), eq(ReceiptStatusType.FAILED));
        verify(receiptCosmosServiceMock).getFailedReceiptByStatus(any(), any(), eq(ReceiptStatusType.NOT_QUEUE_SENT));

        verify(bizEventCosmosClientMock, times(3)).getBizEventDocument(anyString());
    }

    @Test
    void recoverFailedReceiptScheduledDisabled() throws BizEventNotFoundException {
        environment.set("FAILED_AUTORECOVER_ENABLED", "false");
        sut = spy(new RecoverFailedReceiptScheduled(bizEventToReceiptServiceMock, bizEventCosmosClientMock, receiptCosmosServiceMock));

        assertEquals("false", System.getenv("FAILED_AUTORECOVER_ENABLED"));

        // test execution
        assertDoesNotThrow(() -> sut.run("info", documentdb, contextMock));

        verify(documentdb, never()).setValue(any());
        verify(receiptCosmosServiceMock, never()).getFailedReceiptByStatus(any(), any(), any());
        verify(bizEventCosmosClientMock, never()).getBizEventDocument(anyString());

    }

    private BizEvent generateValidBizEvent(String eventId) {
        BizEvent item = new BizEvent();

        Payer payer = new Payer();
        payer.setEntityUniqueIdentifierValue("AAAAAA00A00A000D");
        Debtor debtor = new Debtor();
        debtor.setEntityUniqueIdentifierValue("AAAAAA00A00A000D");

        TransactionDetails transactionDetails = new TransactionDetails();
        Transaction transaction = new Transaction();
        transaction.setCreationDate(String.valueOf(LocalDateTime.now()));
        transactionDetails.setTransaction(transaction);

        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setTotalNotice("1");

        item.setEventStatus(BizEventStatusType.DONE);
        item.setId(eventId);
        item.setPayer(payer);
        item.setDebtor(debtor);
        item.setTransactionDetails(transactionDetails);
        item.setPaymentInfo(paymentInfo);

        return item;
    }

    private Receipt createFailedReceipt(String id, ReceiptStatusType statusType) {
        Receipt receipt = new Receipt();

        receipt.setId(id);
        receipt.setEventId(id);
        receipt.setVersion("1");

        receipt.setStatus(statusType);
        EventData eventData = new EventData();
        eventData.setDebtorFiscalCode(TOKENIZED_DEBTOR_FISCAL_CODE);
        eventData.setPayerFiscalCode(TOKENIZED_PAYER_FISCAL_CODE);
        receipt.setEventData(eventData);

        CartItem item = new CartItem();
        List<CartItem> cartItems = Collections.singletonList(item);
        eventData.setCart(cartItems);

        return receipt;
    }
}