package it.gov.pagopa.receipt.pdf.datastore.helpdesk.http;

import com.azure.core.http.rest.Response;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.storage.queue.models.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.*;
import it.gov.pagopa.receipt.pdf.datastore.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptQueueClient;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.CartQueueClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.*;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.CartItem;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.datastore.service.PDVTokenizerServiceRetryWrapper;
import it.gov.pagopa.receipt.pdf.datastore.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.BizEventToReceiptServiceImpl;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecoverFailedReceiptTest {

    private final String PAYER_FISCAL_CODE = "AAAAAA00A00A000D";
    private final String DEBTOR_FISCAL_CODE = "AAAAAA00A00A000P";
    private final String TOKENIZED_DEBTOR_FISCAL_CODE = "tokenizedDebtorFiscalCode";
    private final String TOKENIZED_PAYER_FISCAL_CODE = "tokenizedPayerFiscalCode";
    private final String EVENT_ID = "a valid id";

    public static final String HTTP_MESSAGE_ERROR = "an error occured";

    @Mock
    private ExecutionContext contextMock;
    @Mock
    private PDVTokenizerServiceRetryWrapper pdvTokenizerServiceMock;
    @Mock
    private ReceiptCosmosService receiptCosmosServiceMock;
    @Mock
    private CartReceiptsCosmosClient cartReceiptsCosmosClientMock;
    @Mock
    private ReceiptQueueClient queueClientMock;
    @Mock
    private BizEventCosmosClientImpl bizEventCosmosClientMock;
    @Mock
    private ReceiptCosmosClientImpl receiptCosmosClient;
    @Mock
    private HttpRequestMessage<Optional<String>> requestMock;
    @Mock
    private CartQueueClientImpl cartQueueClientMock;

    @Captor
    private ArgumentCaptor<Receipt> receiptCaptor;

    @Spy
    private OutputBinding<Receipt> documentdb;

    private AutoCloseable closeable;

    private RecoverFailedReceipt sut;

    @BeforeEach
    void openMocks() {
        closeable = MockitoAnnotations.openMocks(this);
        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock,
                receiptCosmosClient,
                cartReceiptsCosmosClientMock,
                bizEventCosmosClientMock,
                queueClientMock,
                cartQueueClientMock
        );
        sut = spy(new RecoverFailedReceipt(receiptService, bizEventCosmosClientMock, receiptCosmosServiceMock));
    }

    @AfterEach
    void releaseMocks() throws Exception {
        closeable.close();
    }

    @Test
    @SneakyThrows
    void requestOnValidBizEventShouldNotCreateRequest() {
        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID))
                .thenReturn(generateValidBizEvent("1"));

        when(receiptCosmosServiceMock.getReceipt(EVENT_ID)).thenThrow(ReceiptNotFoundException.class);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());
    }

//    @Test
//    @SneakyThrows
//    void requestOnValidCartShouldCreateRequest() {
//        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE))
//                .thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);
//
//        Response<SendMessageResult> queueResponse = mock(Response.class);
//        when(queueResponse.getStatusCode()).thenReturn(HttpStatus.CREATED.value());
//        when(queueClientMock.sendMessageToQueue(anyString())).thenReturn(queueResponse);
//
//        when(requestMock.getQueryParameters()).thenReturn(Collections.singletonMap("isCart","true"));
//
//        FeedResponse feedResponseMock = mock(FeedResponse.class);
//        List<BizEvent> receiptList = Collections.singletonList(generateValidBizEvent("1"));
//        when(feedResponseMock.getResults()).thenReturn(receiptList);
//        doReturn(Collections.singletonList(feedResponseMock)).when(bizEventCosmosClientMock)
//                .getAllBizEventDocument(Mockito.eq("a valid id"), any(), any());
//
//        when(receiptCosmosServiceMock.getReceipt(EVENT_ID)).thenThrow(ReceiptNotFoundException.class);
//
//        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
//            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
//            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
//        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));
//
//        // test execution
//        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));
//
//        // test assertion
//        assertNotNull(response);
//        assertEquals(HttpStatus.OK, response.getStatus());
//        assertNotNull(response.getBody());
//
//        verify(documentdb).setValue(receiptCaptor.capture());
//        Receipt captured = receiptCaptor.getValue();
//        assertEquals(ReceiptStatusType.INSERTED, captured.getStatus());
//        assertEquals(EVENT_ID, captured.getEventId());
//        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, captured.getEventData().getDebtorFiscalCode());
//        assertNotNull(captured.getEventData().getCart());
//        assertEquals(1, captured.getEventData().getCart().size());
//    }

    @Test
    void requestOnValidBizEventAndFailedReceiptShouldResend() throws BizEventNotFoundException, ReceiptNotFoundException, PDVTokenizerException, JsonProcessingException {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE))
                .thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);

        when(receiptCosmosServiceMock.getReceipt(EVENT_ID)).thenReturn(createFailedReceipt());

        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID))
                .thenReturn(generateValidBizEvent("1"));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt captured = receiptCaptor.getValue();
        assertEquals(ReceiptStatusType.FAILED, captured.getStatus());
        assertEquals(EVENT_ID, captured.getEventId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, captured.getEventData().getDebtorFiscalCode());
        assertNotNull(captured.getEventData().getCart());
        assertEquals(1, captured.getEventData().getCart().size());
    }

//    @Test
//    void requestOnValidCartAndFailedReceiptShouldResend() throws ReceiptNotFoundException {
//        when(receiptCosmosServiceMock.getReceipt(EVENT_ID)).thenReturn(createFailedReceipt());
//
//        Response<SendMessageResult> queueResponse = mock(Response.class);
//        when(queueResponse.getStatusCode()).thenReturn(HttpStatus.CREATED.value());
//        when(queueClientMock.sendMessageToQueue(anyString())).thenReturn(queueResponse);
//
//        FeedResponse feedResponseMock = mock(FeedResponse.class);
//        List<BizEvent> receiptList = Collections.singletonList(generateValidBizEvent("1"));
//        when(feedResponseMock.getResults()).thenReturn(receiptList);
//        doReturn(Collections.singletonList(feedResponseMock)).when(bizEventCosmosClientMock)
//                .getAllBizEventDocument(Mockito.eq("a valid id"), any(), any());
//
//        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
//            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
//            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
//        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));
//
//        when(requestMock.getQueryParameters()).thenReturn(Collections.singletonMap("isCart","true"));
//
//        // test execution
//        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));
//
//        // test assertion
//        assertNotNull(response);
//        assertEquals(HttpStatus.OK, response.getStatus());
//        assertNotNull(response.getBody());
//
//        verify(documentdb).setValue(receiptCaptor.capture());
//        Receipt captured = receiptCaptor.getValue();
//        assertEquals(ReceiptStatusType.INSERTED, captured.getStatus());
//        assertEquals(EVENT_ID, captured.getEventId());
//        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, captured.getEventData().getPayerFiscalCode());
//        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, captured.getEventData().getDebtorFiscalCode());
//        assertNotNull(captured.getEventData().getCart());
//        assertEquals(1, captured.getEventData().getCart().size());
//    }

    @Test
    @SneakyThrows
    void requestOnValidBizEventAndFailedReceiptWithoutEventDataShouldUpdateWithToken() {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE))
                .thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);

        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE))
                .thenReturn(TOKENIZED_PAYER_FISCAL_CODE);

        Receipt receipt = createFailedReceipt();
        receipt.setEventData(null);
        when(receiptCosmosServiceMock.getReceipt(EVENT_ID)).thenReturn(receipt);

        BizEvent bizEvent = generateValidBizEvent("1");
        bizEvent.getTransactionDetails().getTransaction().setOrigin("IO");

        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID))
                .thenReturn(bizEvent);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt captured = receiptCaptor.getValue();
        assertEquals(ReceiptStatusType.FAILED, captured.getStatus());
        assertEquals(EVENT_ID, captured.getEventId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, captured.getEventData().getDebtorFiscalCode());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, captured.getEventData().getPayerFiscalCode());
        assertNotNull(captured.getEventData().getCart());
        assertEquals(1, captured.getEventData().getCart().size());
    }

    @Test
    void requestWithMissingBizEventOnRequestIdShouldReturnNotFound() throws BizEventNotFoundException {
        BizEventNotFoundException exception = new BizEventNotFoundException("BizEvent not found");
        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID)).thenThrow(exception);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
        assertNotNull(response.getBody());
    }

    @Test
    void recoverFailedReceiptFailMissingEventId() {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, null, documentdb, contextMock));

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());

        ProblemJson problemJson = (ProblemJson) response.getBody();
        assertNotNull(problemJson);
        assertEquals(HttpStatus.BAD_REQUEST.value(), problemJson.getStatus());
        assertEquals(HttpStatus.BAD_REQUEST.name(), problemJson.getTitle());
        assertNotNull(problemJson.getDetail());
    }

    @Test
    void runDiscardedWithEventNotDONE() throws BizEventNotFoundException {
        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID)).thenReturn(generateNotDoneBizEvent());

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());

        verifyNoInteractions(receiptCosmosServiceMock);
        verifyNoInteractions(queueClientMock);
    }


    @Test
    void generateAnonymousDebtorBizEvent() throws BizEventNotFoundException {
        BizEvent bizEvent = generateAnonymDebtorBizEvent();
        bizEvent.setPayer(null);
        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID)).thenReturn(bizEvent);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());

        verifyNoInteractions(receiptCosmosServiceMock);
        verifyNoInteractions(queueClientMock);
    }

    @Test
    void runDiscardedWithEventNull() throws BizEventNotFoundException {
        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID)).thenReturn(null);

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());

        verifyNoInteractions(receiptCosmosServiceMock);
        verifyNoInteractions(queueClientMock);
    }

    @Test
    void runDiscardedEventWithInvalidTotalNotice() throws BizEventNotFoundException {
        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID))
                .thenReturn(generateValidBizEvent("invalid string"));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());

        verifyNoInteractions(receiptCosmosServiceMock);
        verifyNoInteractions(queueClientMock);
    }

//    @Test
//    void runDiscardedWithInvalidCartAmounts() throws BizEventNotFoundException {
//        BizEvent bizEvent = generateValidBizEvent(null);
//        bizEvent.getTransactionDetails().getTransaction().setAmount(10);
//        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID))
//                .thenReturn(bizEvent);
//
//        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
//            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
//            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
//        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));
//
//        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));
//
//        // test assertion
//        assertNotNull(response);
//        assertEquals(HttpStatus.OK, response.getStatus());
//        assertNotNull(response.getBody());
//
//        verifyNoInteractions(receiptCosmosServiceMock);
//        verifyNoInteractions(queueClientMock);
//
//    }

    @Test
    @SneakyThrows
    void errorTokenizingFiscalCodes() {
        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID)).thenReturn(generateValidBizEvent("1"));

        Receipt receipt = createFailedReceipt();
        receipt.setEventData(null);
        when(receiptCosmosServiceMock.getReceipt(EVENT_ID)).thenReturn(receipt);

        lenient().when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE))
                .thenThrow(new PDVTokenizerException(HTTP_MESSAGE_ERROR, org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR));


        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());

        ProblemJson problemJson = (ProblemJson) response.getBody();
        assertNotNull(problemJson);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), problemJson.getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.name(), problemJson.getTitle());
        assertNotNull(problemJson.getDetail());

        verifyNoInteractions(queueClientMock);
    }

    @Test
    @SneakyThrows
    void errorAddingMessageToQueue() {
        CosmosItemResponse<Receipt> cosmosResponse = mock(CosmosItemResponse.class);
        when(cosmosResponse.getStatusCode()).thenReturn(HttpStatus.CREATED.value());

        when(receiptCosmosClient.saveReceipts(any(Receipt.class))).thenReturn(cosmosResponse);

        Response<SendMessageResult> queueResponse = mock(Response.class);
        when(queueResponse.getStatusCode()).thenReturn(HttpStatus.FORBIDDEN.value());
        when(queueClientMock.sendMessageToQueue(anyString())).thenReturn(queueResponse);

        Receipt receipt = createFailedReceipt();
        receipt.setEventData(null);
        when(receiptCosmosServiceMock.getReceipt(EVENT_ID)).thenReturn(receipt);

        when(bizEventCosmosClientMock.getBizEventDocument(EVENT_ID)).thenReturn(generateValidBizEvent("1"));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }

    private BizEvent generateValidBizEvent(String totalNotice){
        BizEvent item = new BizEvent();

        Payer payer = new Payer();
        payer.setEntityUniqueIdentifierValue(PAYER_FISCAL_CODE);
        Debtor debtor = new Debtor();
        debtor.setEntityUniqueIdentifierValue(DEBTOR_FISCAL_CODE);

        TransactionDetails transactionDetails = new TransactionDetails();
        Transaction transaction = new Transaction();
        transaction.setCreationDate(String.valueOf(LocalDateTime.now()));
        transaction.setAmount(10000);
        transactionDetails.setTransaction(transaction);
        transactionDetails.setOrigin("INFO");

        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setTotalNotice(totalNotice);
        paymentInfo.setAmount("100.0");

        item.setEventStatus(BizEventStatusType.DONE);
        item.setId(EVENT_ID);
        item.setPayer(payer);
        item.setDebtor(debtor);
        item.setTransactionDetails(transactionDetails);
        item.setPaymentInfo(paymentInfo);

        return item;
    }

    private BizEvent generateValidBizEventWithTDetails(String totalNotice) {
        BizEvent item = new BizEvent();

        Debtor debtor = new Debtor();
        debtor.setEntityUniqueIdentifierValue(DEBTOR_FISCAL_CODE);

        TransactionDetails transactionDetails = new TransactionDetails();
        transactionDetails.setInfo(InfoTransaction.builder().clientId("IO").build());
        Transaction transaction = new Transaction();
        transaction.setCreationDate(String.valueOf(LocalDateTime.now()));
        transactionDetails.setTransaction(transaction);
        transactionDetails.setUser(User.builder().fiscalCode(PAYER_FISCAL_CODE).build());

        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setTotalNotice(totalNotice);

        item.setEventStatus(BizEventStatusType.DONE);
        item.setId(EVENT_ID);
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
        eventData.setDebtorFiscalCode(TOKENIZED_DEBTOR_FISCAL_CODE);
        eventData.setPayerFiscalCode(TOKENIZED_PAYER_FISCAL_CODE);
        receipt.setEventData(eventData);

        CartItem item = new CartItem();
        List<CartItem> cartItems = Collections.singletonList(item);
        eventData.setCart(cartItems);

        return receipt;
    }

    private BizEvent generateAnonymDebtorBizEvent(){
        BizEvent item = new BizEvent();

        Payer payer = new Payer();
        payer.setEntityUniqueIdentifierValue(PAYER_FISCAL_CODE);
        Debtor debtor = new Debtor();
        debtor.setEntityUniqueIdentifierValue("ANONIMO");

        TransactionDetails transactionDetails = new TransactionDetails();
        Transaction transaction = new Transaction();
        transaction.setCreationDate(String.valueOf(LocalDateTime.now()));
        transactionDetails.setTransaction(transaction);

        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setTotalNotice("1");

        item.setEventStatus(BizEventStatusType.DONE);
        item.setId(EVENT_ID);
        item.setPayer(payer);
        item.setDebtor(debtor);
        item.setTransactionDetails(transactionDetails);
        item.setPaymentInfo(paymentInfo);

        return item;
    }

    private BizEvent generateNotDoneBizEvent(){
        BizEvent item = new BizEvent();
        item.setEventStatus(BizEventStatusType.NA);
        return item;
    }
}