package it.gov.pagopa.receipt.pdf.datastore;

import com.azure.core.http.rest.Response;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.storage.queue.models.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.CartReceiptsCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.*;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.service.PDVTokenizerServiceRetryWrapper;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.BizEventToReceiptServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariable;

@ExtendWith({MockitoExtension.class, SystemStubsExtension.class})
class BizEventToReceiptTest {
    public static final String HTTP_MESSAGE_ERROR = "an error occured";
    private final String PAYER_FISCAL_CODE = "AAAAAA00A00A000D";
    private final String DEBTOR_FISCAL_CODE = "AAAAAA00A00A000P";
    private final String TOKENIZED_DEBTOR_FISCAL_CODE = "tokenizedDebtorFiscalCode";
    private final String TOKENIZED_PAYER_FISCAL_CODE = "tokenizedPayerFiscalCode";
    private final String EVENT_ID = "a valid id";

    @SystemStub
    private EnvironmentVariables environmentVariables = new EnvironmentVariables(
            "ECOMMERCE_FILTER_ENABLED", "true",
            "ENABLE_CART", "true");

    private BizEventToReceipt function;
    @Mock
    private ExecutionContext context;
    @Mock
    private PDVTokenizerServiceRetryWrapper pdvTokenizerServiceMock;
    @Mock
    private ReceiptCosmosClientImpl receiptCosmosClient;
    @Mock
    private CartReceiptsCosmosClientImpl cartReceiptsCosmosClient;
    @Mock
    private BizEventCosmosClient bizEventCosmosClientMock;
    @Mock
    private ReceiptQueueClientImpl queueClient;

    @Captor
    private ArgumentCaptor<List<Receipt>> receiptCaptor;

    @Captor
    private ArgumentCaptor<CartForReceipt> cartForReceiptArgumentCaptor;


    @Test
    void runOk() throws PDVTokenizerException, JsonProcessingException {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE)).thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE)).thenReturn(TOKENIZED_PAYER_FISCAL_CODE);

        CosmosItemResponse<Receipt> cosmosResponse = mock(CosmosItemResponse.class);
        when(cosmosResponse.getStatusCode()).thenReturn(HttpStatus.CREATED.value());
        when(receiptCosmosClient.saveReceipts(any(Receipt.class))).thenReturn(cosmosResponse);

        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.CREATED.value());
        when(queueClient.sendMessageToQueue(anyString())).thenReturn(response);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, receiptCosmosClient, cartReceiptsCosmosClient, bizEventCosmosClientMock, queueClient);
        function = new BizEventToReceipt(receiptService);

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent("1"));

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        // test execution
        assertDoesNotThrow(() -> function.processBizEventToReceipt(bizEventItems, documentdb, context));

        verify(documentdb, never()).setValue(any());
    }

    @Test
    void runOk_TDetails() throws PDVTokenizerException, JsonProcessingException {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE)).thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE)).thenReturn(TOKENIZED_PAYER_FISCAL_CODE);

        CosmosItemResponse<Receipt> cosmosResponse = mock(CosmosItemResponse.class);
        when(cosmosResponse.getStatusCode()).thenReturn(HttpStatus.CREATED.value());
        when(receiptCosmosClient.saveReceipts(any(Receipt.class))).thenReturn(cosmosResponse);

        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.CREATED.value());
        when(queueClient.sendMessageToQueue(anyString())).thenReturn(response);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, receiptCosmosClient, cartReceiptsCosmosClient, bizEventCosmosClientMock, queueClient);
        function = new BizEventToReceipt(receiptService);

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEventWithTDetails("1"));

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        // test execution
        assertDoesNotThrow(() -> function.processBizEventToReceipt(bizEventItems, documentdb, context));

        verify(documentdb, never()).setValue(any());
    }


    @Test
    void runOkTotalNoticeNull() throws PDVTokenizerException, JsonProcessingException {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE)).thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE)).thenReturn(TOKENIZED_PAYER_FISCAL_CODE);

        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.CREATED.value());
        when(queueClient.sendMessageToQueue(anyString())).thenReturn(response);

        CosmosItemResponse<Receipt> cosmosResponse = mock(CosmosItemResponse.class);
        when(cosmosResponse.getStatusCode()).thenReturn(HttpStatus.CREATED.value());
        when(receiptCosmosClient.saveReceipts(any(Receipt.class))).thenReturn(cosmosResponse);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, receiptCosmosClient, cartReceiptsCosmosClient, bizEventCosmosClientMock, queueClient);
        function = new BizEventToReceipt(receiptService);

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent(null));

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        // test execution
        assertDoesNotThrow(() -> function.processBizEventToReceipt(bizEventItems, documentdb, context));

        verify(documentdb, never()).setValue(any());
    }

    @Test
    void runDiscardedWithEventNotDONE() {
        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateNotDoneBizEvent());

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, receiptCosmosClient, cartReceiptsCosmosClient, bizEventCosmosClientMock, queueClient);
        function = new BizEventToReceipt(receiptService);
        // test execution
        assertDoesNotThrow(() -> function.processBizEventToReceipt(bizEventItems, documentdb, context));

        verify(documentdb, never()).setValue(any());
    }

    @Test
    void runDiscardedWithAnonymousDebtorAndMissingPayer() {
        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateAnonymDebtorBizEvent("1"));
        bizEventItems.get(0).setPayer(null);

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);
        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, receiptCosmosClient, cartReceiptsCosmosClient, bizEventCosmosClientMock, queueClient);
        function = new BizEventToReceipt(receiptService);
        // test execution
        assertDoesNotThrow(() -> function.processBizEventToReceipt(bizEventItems, documentdb, context));

        verify(documentdb, never()).setValue(any());
    }

    @Test
    void runDiscardedWithECommerceEvent() {
        List<BizEvent> bizEventItems = new ArrayList<>();
        BizEvent bizEvent = generateValidBizEvent("1");
        bizEvent.getTransactionDetails()
                .setInfo(InfoTransaction.builder()
                        .clientId("CHECKOUT")
                        .build());
        bizEventItems.add(bizEvent);

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);
        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, receiptCosmosClient, cartReceiptsCosmosClient, bizEventCosmosClientMock, queueClient);
        function = new BizEventToReceipt(receiptService);
        // test execution
        assertDoesNotThrow(() -> function.processBizEventToReceipt(bizEventItems, documentdb, context));

        verify(documentdb, never()).setValue(any());
    }

    @Test
    void runDiscardedWithReceiptAlreadyInserted() throws ReceiptNotFoundException {
        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent("1"));

        when(receiptCosmosClient.getReceiptDocument(any())).thenReturn(new Receipt());

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);
        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, receiptCosmosClient, cartReceiptsCosmosClient, bizEventCosmosClientMock, queueClient);
        function = new BizEventToReceipt(receiptService);
        // test execution
        assertDoesNotThrow(() -> function.processBizEventToReceipt(bizEventItems, documentdb, context));

        verify(documentdb, never()).setValue(any());
    }

    @Test
    void runDiscardedWithEventNull() {
        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(null);

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);
        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, receiptCosmosClient, cartReceiptsCosmosClient, bizEventCosmosClientMock, queueClient);
        function = new BizEventToReceipt(receiptService);
        // test execution
        assertDoesNotThrow(() -> function.processBizEventToReceipt(bizEventItems, documentdb, context));

        verify(documentdb, never()).setValue(any());
    }

    @Test
    void runDiscardedWithCartEventWithInvalidTotalNotice() {
        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent("invalid string"));

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);
        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, receiptCosmosClient, cartReceiptsCosmosClient, bizEventCosmosClientMock, queueClient);
        function = new BizEventToReceipt(receiptService);
        // test execution
        assertThrows(NumberFormatException.class, () -> function.processBizEventToReceipt(bizEventItems, documentdb, context));

        verify(documentdb, never()).setValue(any());
    }

    @Test
    void errorTokenizingFiscalCodes() throws PDVTokenizerException, JsonProcessingException {
        lenient().when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE))
                .thenThrow(new PDVTokenizerException(HTTP_MESSAGE_ERROR, org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR));

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, receiptCosmosClient, cartReceiptsCosmosClient, bizEventCosmosClientMock, queueClient);
        function = new BizEventToReceipt(receiptService);

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent("1"));

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        // test execution
        assertDoesNotThrow(() -> function.processBizEventToReceipt(bizEventItems, documentdb, context));

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt captured = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.FAILED, captured.getStatus());
        assertEquals(EVENT_ID, captured.getEventId());
        assertNull(captured.getEventData());
        assertEquals(org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR, captured.getReasonErr().getCode());
        assertEquals(HTTP_MESSAGE_ERROR, captured.getReasonErr().getMessage());
    }

    @Test
    void errorAddingMessageToQueue() throws PDVTokenizerException, JsonProcessingException {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE)).thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE)).thenReturn(TOKENIZED_PAYER_FISCAL_CODE);

        CosmosItemResponse<Receipt> cosmosResponse = mock(CosmosItemResponse.class);
        when(cosmosResponse.getStatusCode()).thenReturn(HttpStatus.CREATED.value());
        when(receiptCosmosClient.saveReceipts(any(Receipt.class))).thenReturn(cosmosResponse);

        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(HttpStatus.FORBIDDEN.value());
        when(queueClient.sendMessageToQueue(anyString())).thenReturn(response);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, receiptCosmosClient, cartReceiptsCosmosClient, bizEventCosmosClientMock, queueClient);
        function = new BizEventToReceipt(receiptService);

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent("1"));

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        // test execution
        assertDoesNotThrow(() -> function.processBizEventToReceipt(bizEventItems, documentdb, context));

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt captured = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.NOT_QUEUE_SENT, captured.getStatus());
        assertEquals(HttpStatus.FORBIDDEN.value(), captured.getReasonErr().getCode());
        assertEquals(EVENT_ID, captured.getEventId());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, captured.getEventData().getPayerFiscalCode());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, captured.getEventData().getDebtorFiscalCode());
        assertNotNull(captured.getEventData().getCart());
        assertEquals(1, captured.getEventData().getCart().size());
    }

    @Test
    void errorSavingReceiptToCosmos() throws PDVTokenizerException, JsonProcessingException {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE)).thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE)).thenReturn(TOKENIZED_PAYER_FISCAL_CODE);

        CosmosItemResponse<Receipt> cosmosResponse = mock(CosmosItemResponse.class);
        when(cosmosResponse.getStatusCode()).thenReturn(HttpStatus.FORBIDDEN.value());
        when(receiptCosmosClient.saveReceipts(any(Receipt.class))).thenReturn(cosmosResponse);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, receiptCosmosClient, cartReceiptsCosmosClient, bizEventCosmosClientMock, queueClient);
        function = new BizEventToReceipt(receiptService);

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent("1"));

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        // test execution
        assertDoesNotThrow(() -> function.processBizEventToReceipt(bizEventItems, documentdb, context));

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt captured = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.FAILED, captured.getStatus());
        assertEquals(HttpStatus.FORBIDDEN.value(), captured.getReasonErr().getCode());
        assertEquals(EVENT_ID, captured.getEventId());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, captured.getEventData().getPayerFiscalCode());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, captured.getEventData().getDebtorFiscalCode());
        assertNotNull(captured.getEventData().getCart());
        assertEquals(1, captured.getEventData().getCart().size());
    }

    @Test
    void errorAddingMessageToQueueThrowException() throws Exception {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE)).thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE)).thenReturn(TOKENIZED_PAYER_FISCAL_CODE);

        CosmosItemResponse<Receipt> cosmosResponse = mock(CosmosItemResponse.class);
        when(cosmosResponse.getStatusCode()).thenReturn(HttpStatus.CREATED.value());

        when(receiptCosmosClient.saveReceipts(any(Receipt.class))).thenReturn(cosmosResponse);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, receiptCosmosClient, cartReceiptsCosmosClient, bizEventCosmosClientMock, queueClient);
        function = new BizEventToReceipt(receiptService);

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent("1"));

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        // test execution
        withEnvironmentVariable("RECEIPT_QUEUE_TOPIC", "wrong-queue-topic").execute(() ->
                assertDoesNotThrow(() ->
                        function.processBizEventToReceipt(bizEventItems, documentdb, context))
        );

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt captured = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.NOT_QUEUE_SENT, captured.getStatus());
        assertEquals(ReasonErrorCode.ERROR_QUEUE.getCode(), captured.getReasonErr().getCode());
        assertEquals(EVENT_ID, captured.getEventId());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, captured.getEventData().getPayerFiscalCode());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, captured.getEventData().getDebtorFiscalCode());
        assertNotNull(captured.getEventData().getCart());
        assertEquals(1, captured.getEventData().getCart().size());
    }

    @Test
    void errorSavingReceiptToCosmosThrowException() throws Exception {
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE)).thenReturn(TOKENIZED_DEBTOR_FISCAL_CODE);
        when(pdvTokenizerServiceMock.generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE)).thenReturn(TOKENIZED_PAYER_FISCAL_CODE);

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, receiptCosmosClient, cartReceiptsCosmosClient, bizEventCosmosClientMock, queueClient);
        function = new BizEventToReceipt(receiptService);

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent("1"));

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        // test execution
        withEnvironmentVariable("COSMOS_RECEIPT_SERVICE_ENDPOINT", "wrong-endpoint").execute(() ->
                assertDoesNotThrow(() ->
                        function.processBizEventToReceipt(bizEventItems, documentdb, context))
        );

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt captured = receiptCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.FAILED, captured.getStatus());
        assertEquals(ReasonErrorCode.ERROR_COSMOS.getCode(), captured.getReasonErr().getCode());
        assertEquals(EVENT_ID, captured.getEventId());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, captured.getEventData().getPayerFiscalCode());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, captured.getEventData().getDebtorFiscalCode());
        assertNotNull(captured.getEventData().getCart());
        assertEquals(1, captured.getEventData().getCart().size());
    }

    @Test
    void runOk_CartToCreate() throws CartNotFoundException {

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, receiptCosmosClient, cartReceiptsCosmosClient, bizEventCosmosClientMock, queueClient);
        function = new BizEventToReceipt(receiptService);

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent("2"));

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        // test execution
        assertDoesNotThrow(() -> function.processBizEventToReceipt(bizEventItems, documentdb, context));

        verify(documentdb, never()).setValue(any());
        verify(cartReceiptsCosmosClient).getCartItem(any());
        verify(cartReceiptsCosmosClient).saveCart(cartForReceiptArgumentCaptor.capture());
        CartForReceipt cartForReceipt = cartForReceiptArgumentCaptor.getValue();
        assertEquals(1, cartForReceipt.getCartPaymentId().size());
    }

    @Test
    void runOk_CartToCreate_ExistingCart() throws CartNotFoundException {

        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, receiptCosmosClient, cartReceiptsCosmosClient, bizEventCosmosClientMock, queueClient);
        function = new BizEventToReceipt(receiptService);

        when(cartReceiptsCosmosClient.getCartItem(any())).thenReturn(CartForReceipt.builder().id("2332").totalNotice(2)
                .cartPaymentId(new HashSet<>(Collections.singletonList("1"))).build());

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent("2"));

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        // test execution
        assertDoesNotThrow(() -> function.processBizEventToReceipt(bizEventItems, documentdb, context));

        verify(documentdb, never()).setValue(any());
        verify(cartReceiptsCosmosClient).getCartItem(any());
        verify(cartReceiptsCosmosClient).updateCart(cartForReceiptArgumentCaptor.capture());
        CartForReceipt cartForReceipt = cartForReceiptArgumentCaptor.getValue();
        assertEquals(2, cartForReceipt.getCartPaymentId().size());
    }


    @Test
    void bizEventNotProcessedCartNotEnabled() throws CartNotFoundException {
        environmentVariables.set("ENABLE_CART", "false");
        BizEventToReceiptServiceImpl receiptService = new BizEventToReceiptServiceImpl(
                pdvTokenizerServiceMock, receiptCosmosClient, cartReceiptsCosmosClient, bizEventCosmosClientMock, queueClient);
        function = new BizEventToReceipt(receiptService);

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent("2"));

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        // test execution
        assertDoesNotThrow(() -> function.processBizEventToReceipt(bizEventItems, documentdb, context));

        verify(documentdb, never()).setValue(any());
        verify(cartReceiptsCosmosClient, never()).getCartItem(any());
        verify(cartReceiptsCosmosClient, never()).saveCart(any());
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

    private BizEvent generateValidBizEventWithTDetails(String totalNotice){
        BizEvent item = new BizEvent();

        Debtor debtor = new Debtor();
        debtor.setEntityUniqueIdentifierValue(DEBTOR_FISCAL_CODE);

        TransactionDetails transactionDetails = new TransactionDetails();
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

    private BizEvent generateAnonymDebtorBizEvent(String totalNotice){
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
        paymentInfo.setTotalNotice(totalNotice);

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
