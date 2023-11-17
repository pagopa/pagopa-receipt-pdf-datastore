package it.gov.pagopa.receipt.pdf.datastore;

import com.azure.core.http.rest.Response;
import com.azure.cosmos.models.ModelBridgeInternal;
import com.azure.storage.queue.models.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.*;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptQueueClient;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.*;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.CartItem;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.model.ReceiptFailedRecoveryRequest;
import it.gov.pagopa.receipt.pdf.datastore.service.PDVTokenizerServiceRetryWrapper;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.BizEventToReceiptServiceImpl;
import it.gov.pagopa.receipt.pdf.datastore.util.HttpResponseMessageMock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.microsoft.azure.functions.HttpStatus.BAD_REQUEST;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecoverFailedReceiptTest {

    private final String PAYER_FISCAL_CODE = "a valid payer CF";
    private final String DEBTOR_FISCAL_CODE = "a valid debtor CF";
    private final String TOKENIZED_DEBTOR_FISCAL_CODE = "tokenizedDebtorFiscalCode";
    private final String TOKENIZED_PAYER_FISCAL_CODE = "tokenizedPayerFiscalCode";
    private final String EVENT_ID = "a valid id";

    private RecoverFailedReceipt function;

    @Mock
    private ExecutionContext context;
    @Mock
    private PDVTokenizerServiceRetryWrapper pdvTokenizerServiceMock;
    @Mock
    private ReceiptCosmosClient receiptCosmosClient;
    @Mock
    private ReceiptQueueClient queueClient;
    @Mock
    private BizEventCosmosClientImpl bizEventCosmosClientMock;

    @Captor
    private ArgumentCaptor<List<Receipt>> receiptCaptor;

    @Test
    void requestOnValidBizEventShouldCreateRequest() throws PDVTokenizerException, JsonProcessingException,
            ReceiptNotFoundException, BizEventNotFoundException {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE))
                .thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE))
                .thenReturn(TOKENIZED_PAYER_FISCAL_CODE);

        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.CREATED.value());
        when(queueClient.sendMessageToQueue(anyString())).thenReturn(response);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(pdvTokenizerServiceMock,receiptCosmosClient, queueClient);

        when(bizEventCosmosClientMock.getBizEventDocument(Mockito.eq("1")))
                .thenReturn(generateValidBizEvent("1"));

        when(receiptCosmosClient.getReceiptDocument(Mockito.eq("1"))).thenThrow(ReceiptNotFoundException.class);

        function = new RecoverFailedReceipt(receiptService, bizEventCosmosClientMock, receiptCosmosClient);

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        ReceiptFailedRecoveryRequest receiptFailedRecoveryRequest = new ReceiptFailedRecoveryRequest();
        receiptFailedRecoveryRequest.setEventId("1");

        HttpRequestMessage<Optional<ReceiptFailedRecoveryRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(receiptFailedRecoveryRequest));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        assertDoesNotThrow(() -> function.run(request, documentdb, context));

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt captured = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.INSERTED, captured.getStatus());
        assertEquals(EVENT_ID, captured.getEventId());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, captured.getEventData().getPayerFiscalCode());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, captured.getEventData().getDebtorFiscalCode());
        assertNotNull(captured.getEventData().getCart());
        assertEquals(1, captured.getEventData().getCart().size());
    }

    @Test
    void requestOnValidBizEventAndFailedReceiptShouldResend() throws
            ReceiptNotFoundException, BizEventNotFoundException {
        when(receiptCosmosClient.getReceiptDocument(Mockito.eq("1"))).thenReturn(createFailedReceipt("1"));

        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.CREATED.value());
        when(queueClient.sendMessageToQueue(anyString())).thenReturn(response);
        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(pdvTokenizerServiceMock,receiptCosmosClient, queueClient);

        when(bizEventCosmosClientMock.getBizEventDocument(Mockito.eq("1")))
                .thenReturn(generateValidBizEvent("1"));

        function = new RecoverFailedReceipt(receiptService, bizEventCosmosClientMock, receiptCosmosClient);

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        ReceiptFailedRecoveryRequest receiptFailedRecoveryRequest = new ReceiptFailedRecoveryRequest();
        receiptFailedRecoveryRequest.setEventId("1");

        HttpRequestMessage<Optional<ReceiptFailedRecoveryRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(receiptFailedRecoveryRequest));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        assertDoesNotThrow(() -> function.run(request, documentdb, context));

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt captured = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.INSERTED, captured.getStatus());
        assertEquals("1", captured.getEventId());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, captured.getEventData().getPayerFiscalCode());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, captured.getEventData().getDebtorFiscalCode());
        assertNotNull(captured.getEventData().getCart());
        assertEquals(1, captured.getEventData().getCart().size());
    }


    @Test
    void requestOnValidBizEventAndFailedReceiptListShouldResend() throws BizEventNotFoundException {
        ReceiptCosmosClientImpl receiptCosmosClient = mock(ReceiptCosmosClientImpl.class);
        when(receiptCosmosClient.getFailedReceiptDocuments(Mockito.any(),Mockito.any())).thenReturn(
                Collections.singletonList(ModelBridgeInternal
                        .createFeedResponse(Collections.singletonList(createFailedReceipt("1")),
                                Collections.emptyMap())));

        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.CREATED.value());
        when(queueClient.sendMessageToQueue(anyString())).thenReturn(response);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(pdvTokenizerServiceMock,receiptCosmosClient, queueClient);

        when(bizEventCosmosClientMock.getBizEventDocument(Mockito.eq("1")))
                .thenReturn(generateValidBizEvent("1"));

        function = new RecoverFailedReceipt(receiptService, bizEventCosmosClientMock, receiptCosmosClient);

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        ReceiptFailedRecoveryRequest receiptFailedRecoveryRequest = new ReceiptFailedRecoveryRequest();

        HttpRequestMessage<Optional<ReceiptFailedRecoveryRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(receiptFailedRecoveryRequest));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        assertDoesNotThrow(() -> function.run(request, documentdb, context));

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt captured = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.INSERTED, captured.getStatus());
        assertEquals("1", captured.getEventId());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, captured.getEventData().getPayerFiscalCode());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, captured.getEventData().getDebtorFiscalCode());
        assertNotNull(captured.getEventData().getCart());
        assertEquals(1, captured.getEventData().getCart().size());
    }

    @Test
    void requestOnValidBizEventAndFailedReceiptWithMissingFiscalCodeTokenShouldUpdateWithToken() throws PDVTokenizerException, JsonProcessingException,
            ReceiptNotFoundException, BizEventNotFoundException {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE))
                .thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE))
                .thenReturn(TOKENIZED_PAYER_FISCAL_CODE);

        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.CREATED.value());
        when(queueClient.sendMessageToQueue(anyString())).thenReturn(response);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(pdvTokenizerServiceMock,receiptCosmosClient, queueClient);

        Receipt receipt = createFailedReceipt("1");
        receipt.getEventData().setPayerFiscalCode(null);
        receipt.getEventData().setDebtorFiscalCode(null);
        when(receiptCosmosClient.getReceiptDocument(Mockito.eq("1"))).thenReturn(receipt);

        when(bizEventCosmosClientMock.getBizEventDocument(Mockito.eq("1")))
                .thenReturn(generateValidBizEvent("1"));

        function = new RecoverFailedReceipt(receiptService, bizEventCosmosClientMock, receiptCosmosClient);

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        ReceiptFailedRecoveryRequest receiptFailedRecoveryRequest = new ReceiptFailedRecoveryRequest();
        receiptFailedRecoveryRequest.setEventId("1");

        HttpRequestMessage<Optional<ReceiptFailedRecoveryRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(receiptFailedRecoveryRequest));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        assertDoesNotThrow(() -> function.run(request, documentdb, context));

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt captured = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.INSERTED, captured.getStatus());
        assertEquals("1", captured.getEventId());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, captured.getEventData().getPayerFiscalCode());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, captured.getEventData().getDebtorFiscalCode());
        assertNotNull(captured.getEventData().getCart());
        assertEquals(1, captured.getEventData().getCart().size());
    }

    @Test
    void requestOnValidBizEventAndFailedReceiptWithoutEventDataShouldUpdateWithToken() throws PDVTokenizerException, JsonProcessingException,
            ReceiptNotFoundException, BizEventNotFoundException {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE))
                .thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE))
                .thenReturn(TOKENIZED_PAYER_FISCAL_CODE);

        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.CREATED.value());
        when(queueClient.sendMessageToQueue(anyString())).thenReturn(response);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(pdvTokenizerServiceMock,receiptCosmosClient, queueClient);

        Receipt receipt = createFailedReceipt("1");
        receipt.setEventData(null);
        when(receiptCosmosClient.getReceiptDocument(Mockito.eq("1"))).thenReturn(receipt);

        when(bizEventCosmosClientMock.getBizEventDocument(Mockito.eq("1")))
                .thenReturn(generateValidBizEvent("1"));

        function = new RecoverFailedReceipt(receiptService, bizEventCosmosClientMock, receiptCosmosClient);

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        ReceiptFailedRecoveryRequest receiptFailedRecoveryRequest = new ReceiptFailedRecoveryRequest();
        receiptFailedRecoveryRequest.setEventId("1");

        HttpRequestMessage<Optional<ReceiptFailedRecoveryRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(receiptFailedRecoveryRequest));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        assertDoesNotThrow(() -> function.run(request, documentdb, context));

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt captured = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.INSERTED, captured.getStatus());
        assertEquals("1", captured.getEventId());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, captured.getEventData().getPayerFiscalCode());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, captured.getEventData().getDebtorFiscalCode());
        assertNotNull(captured.getEventData().getCart());
        assertEquals(1, captured.getEventData().getCart().size());
    }

    @Test
    void requestWithMissingBizEventOnRequestIdShouldReturnBadRequest() throws BizEventNotFoundException {

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(pdvTokenizerServiceMock,receiptCosmosClient, queueClient);

        when(bizEventCosmosClientMock.getBizEventDocument(Mockito.eq("1")))
                .thenThrow(BizEventNotFoundException.class);

        function = new RecoverFailedReceipt(receiptService, bizEventCosmosClientMock, receiptCosmosClient);

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        ReceiptFailedRecoveryRequest receiptFailedRecoveryRequest = new ReceiptFailedRecoveryRequest();
        receiptFailedRecoveryRequest.setEventId("1");

        HttpRequestMessage<Optional<ReceiptFailedRecoveryRequest>> request = mock(HttpRequestMessage.class);
        when(request.getBody()).thenReturn(Optional.of(receiptFailedRecoveryRequest));

        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(request).createResponseBuilder(any(HttpStatus.class));

        // test execution
        HttpResponseMessage httpResponseMessage = assertDoesNotThrow(() -> function.run(request, documentdb, context));

        assertNotNull(httpResponseMessage);
        assertEquals(BAD_REQUEST.value(), httpResponseMessage.getStatus().value());

    }

    private static void setMock(ReceiptQueueClientImpl mock) {
        try {
            Field instance = ReceiptQueueClientImpl.class.getDeclaredField("instance");
            instance.setAccessible(true);
            instance.set(instance, mock);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setMock(ReceiptCosmosClientImpl mock) {
        try {
            Field instance = ReceiptCosmosClientImpl.class.getDeclaredField("instance");
            instance.setAccessible(true);
            instance.set(instance, mock);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setMock(BizEventCosmosClientImpl mock) {
        try {
            Field instance = BizEventCosmosClientImpl.class.getDeclaredField("instance");
            instance.setAccessible(true);
            instance.set(instance, mock);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        transactionDetails.setTransaction(transaction);

        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setTotalNotice(totalNotice);

        item.setEventStatus(BizEventStatusType.DONE);
        item.setId(EVENT_ID);
        item.setPayer(payer);
        item.setDebtor(debtor);
        item.setTransactionDetails(transactionDetails);
        item.setPaymentInfo(paymentInfo);

        return item;
    }

    private Receipt createFailedReceipt(String eventId) {
        Receipt receipt = new Receipt();

        receipt.setId(eventId);
        receipt.setEventId(eventId);

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

}