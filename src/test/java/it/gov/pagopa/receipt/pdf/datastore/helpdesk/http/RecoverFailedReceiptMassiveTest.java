package it.gov.pagopa.receipt.pdf.datastore.helpdesk.http;

import com.azure.cosmos.models.ModelBridgeInternal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.*;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.*;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.CartItem;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.datastore.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.datastore.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.datastore.utils.HttpResponseMessageMock;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecoverFailedReceiptMassiveTest {

    private final String tokenizedDebtorFiscalCode = "tokenizedDebtorFiscalCode";
    private final String tokenizedPayerFiscalCode = "tokenizedPayerFiscalCode";
    private final String eventId = "a valid id";

    @Mock
    private ExecutionContext contextMock;
    @Mock
    private ReceiptCosmosService receiptCosmosServiceMock;
    @Mock
    private BizEventCosmosClientImpl bizEventCosmosClientMock;
    @Mock
    private BizEventToReceiptService bizEventToReceiptServiceMock;

    @Mock
    private HttpRequestMessage<Optional<String>> requestMock;

    @Captor
    private ArgumentCaptor<List<Receipt>> receiptCaptor;

    @Spy
    private OutputBinding<List<Receipt>> documentdb;

    private AutoCloseable closeable;

    private RecoverFailedReceiptMassive sut;

    @BeforeEach
    void openMocks() {
        closeable = MockitoAnnotations.openMocks(this);
        sut = spy(new RecoverFailedReceiptMassive(bizEventToReceiptServiceMock, bizEventCosmosClientMock, receiptCosmosServiceMock));
    }

    @AfterEach
    void releaseMocks() throws Exception {
        closeable.close();
    }

    @Test
    void recoverFailedReceiptMassiveSuccess() throws BizEventNotFoundException, PDVTokenizerException, JsonProcessingException {
        when(requestMock.getQueryParameters())
                .thenReturn(Collections.singletonMap("status", ReceiptStatusType.IO_ERROR_TO_NOTIFY.name()));

        when(receiptCosmosServiceMock.getFailedReceiptByStatus(any(), any(), any()))
                .thenReturn(Collections.singletonList(ModelBridgeInternal
                        .createFeedResponse(Collections.singletonList(createFailedReceipt()),
                                Collections.emptyMap())));

        when(bizEventCosmosClientMock.getBizEventDocument(eventId))
                .thenReturn(generateValidBizEvent(eventId));

        Answer<Void> successAnswer = invocation -> {
            // arg 0: BizEvent, arg 1: Receipt, arg 2: EventData
            EventData eventDataArg = invocation.getArgument(2);

            // simulate tokenization
            eventDataArg.setPayerFiscalCode(tokenizedPayerFiscalCode);
            eventDataArg.setDebtorFiscalCode(tokenizedDebtorFiscalCode);
            return null;
        };

        doAnswer(successAnswer).when(bizEventToReceiptServiceMock).tokenizeFiscalCodes(any(), any(), any());

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb).setValue(receiptCaptor.capture());
        assertEquals(1, receiptCaptor.getValue().size());
        Receipt captured = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.INSERTED, captured.getStatus());
        assertEquals(eventId, captured.getEventId());
        assertEquals(tokenizedPayerFiscalCode, captured.getEventData().getPayerFiscalCode());
        assertEquals(tokenizedDebtorFiscalCode, captured.getEventData().getDebtorFiscalCode());
        assertNotNull(captured.getEventData().getCart());
        assertEquals(1, captured.getEventData().getCart().size());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptMassiveFailMissingStatusParam() {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());

        ProblemJson problemJson = (ProblemJson) response.getBody();
        assertNotNull(problemJson);
        assertEquals(HttpStatus.BAD_REQUEST.value(), problemJson.getStatus());
        assertEquals(HttpStatus.BAD_REQUEST.name(), problemJson.getTitle());
        assertNotNull(problemJson.getDetail());

        verify(documentdb, never()).setValue(receiptCaptor.capture());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptMassiveFailInvalidStatusParam() {
        when(requestMock.getQueryParameters())
                .thenReturn(Collections.singletonMap("status", "INVALID_STATUS"));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());

        ProblemJson problemJson = (ProblemJson) response.getBody();
        assertNotNull(problemJson);
        assertEquals(HttpStatus.BAD_REQUEST.value(), problemJson.getStatus());
        assertEquals(HttpStatus.BAD_REQUEST.name(), problemJson.getTitle());
        assertNotNull(problemJson.getDetail());

        verify(documentdb, never()).setValue(receiptCaptor.capture());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptMassivePartialOK() {
        when(requestMock.getQueryParameters())
                .thenReturn(Collections.singletonMap("status", ReceiptStatusType.IO_ERROR_TO_NOTIFY.name()));

        List<Receipt> receiptList = new ArrayList<>();
        Receipt receipt1 = createFailedReceipt();
        receiptList.add(receipt1);

        Receipt receipt2 = createFailedReceipt();
        receipt2.setStatus(ReceiptStatusType.INSERTED);
        receipt2.setEventData(null);
        receiptList.add(receipt2);

        when(receiptCosmosServiceMock.getFailedReceiptByStatus(any(), any(), any()))
                .thenReturn(Collections.singletonList(ModelBridgeInternal
                        .createFeedResponse(receiptList, Collections.emptyMap())));

        Answer<Void> successAnswer = invocation -> {
            // arg 0: BizEvent, arg 1: Receipt, arg 2: EventData
            EventData eventDataArg = invocation.getArgument(2);

            // simulate tokenization
            eventDataArg.setPayerFiscalCode(tokenizedPayerFiscalCode);
            eventDataArg.setDebtorFiscalCode(tokenizedDebtorFiscalCode);
            return null;
        };

        doThrow(PDVTokenizerException.class) // 1. first call: error
                .doAnswer(successAnswer)     // 2. next call: success
                .when(bizEventToReceiptServiceMock).tokenizeFiscalCodes(any(), any(), any());

        String bizOk = "a valid id 2";
        when(bizEventCosmosClientMock.getBizEventDocument(anyString()))
                .thenReturn(generateValidBizEvent(eventId))
                .thenReturn(generateValidBizEvent(bizOk));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));


        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());

        ProblemJson problemJson = (ProblemJson) response.getBody();
        assertNotNull(problemJson);
        assertEquals(HttpStatus.MULTI_STATUS.value(), problemJson.getStatus());
        assertEquals("Partial OK", problemJson.getTitle());
        assertNotNull(problemJson.getDetail());

        verify(documentdb).setValue(receiptCaptor.capture());
        assertEquals(1, receiptCaptor.getValue().size());
        Receipt captured = receiptCaptor.getValue().get(0);
        assertEquals(bizOk, captured.getEventId());
        assertEquals(tokenizedPayerFiscalCode, captured.getEventData().getPayerFiscalCode());
        assertEquals(tokenizedDebtorFiscalCode, captured.getEventData().getDebtorFiscalCode());
        assertNotNull(captured.getEventData().getCart());
        assertEquals(1, captured.getEventData().getCart().size());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptMassiveFailNoSuchElementInIterator() {
        when(requestMock.getQueryParameters())
                .thenReturn(Collections.singletonMap("status", ReceiptStatusType.IO_ERROR_TO_NOTIFY.name()));

        Iterable iterableMock = mock(Iterable.class);
        Iterator iteratorMock = mock(Iterator.class);
        when(iterableMock.iterator()).thenReturn(iteratorMock);
        when(iteratorMock.hasNext()).thenThrow(new NoSuchElementException(""));
        when(receiptCosmosServiceMock.getFailedReceiptByStatus(any(), any(), any()))
                .thenReturn(iterableMock);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());

        ProblemJson problemJson = (ProblemJson) response.getBody();
        assertNotNull(problemJson);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problemJson.getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.name(), problemJson.getTitle());
        assertNotNull(problemJson.getDetail());

        verify(documentdb, never()).setValue(receiptCaptor.capture());
    }

    private BizEvent generateValidBizEvent(String eventId){
        BizEvent item = new BizEvent();

        Payer payer = new Payer();
        payer.setEntityUniqueIdentifierValue("AAAAAA00A00A000P");
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

    private Receipt createFailedReceipt() {
        Receipt receipt = new Receipt();

        receipt.setId("a valid id");
        receipt.setEventId("a valid id");
        receipt.setVersion("1");

        receipt.setStatus(ReceiptStatusType.FAILED);
        EventData eventData = new EventData();
        eventData.setDebtorFiscalCode(tokenizedDebtorFiscalCode);
        eventData.setPayerFiscalCode(tokenizedPayerFiscalCode);
        receipt.setEventData(eventData);

        CartItem item = new CartItem();
        List<CartItem> cartItems = Collections.singletonList(item);
        eventData.setCart(cartItems);

        return receipt;
    }
}