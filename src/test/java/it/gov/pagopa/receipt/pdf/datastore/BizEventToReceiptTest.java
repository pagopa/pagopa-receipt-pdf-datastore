package it.gov.pagopa.receipt.pdf.datastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartPayment;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.Payload;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.Debtor;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.InfoTransaction;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.Payer;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.PaymentInfo;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.Transaction;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.TransactionDetails;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.User;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.BizEventToReceiptServiceImpl;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith({MockitoExtension.class, SystemStubsExtension.class})
class BizEventToReceiptTest {
    private static final String HTTP_MESSAGE_ERROR = "an error occured";
    private static final String VALID_IO_CHANNEL = "IO";
    private static final String PAYER_FISCAL_CODE = "AAAAAA00A00A000D";
    private static final String DEBTOR_FISCAL_CODE = "AAAAAA00A00A000P";
    private static final String TOKENIZED_DEBTOR_FISCAL_CODE = "tokenizedDebtorFiscalCode";
    private static final String TOKENIZED_DEBTOR_FISCAL_CODE_2 = "tokenizedDebtorFiscalCode2";
    private static final String TOKENIZED_PAYER_FISCAL_CODE = "tokenizedPayerFiscalCode";
    private static final String EVENT_ID = "a valid id";
    private static final String EVENT_ID_2 = "a valid id 2";
    private static final String CART_ID = "a valid cart id";
    private static final String CREATION_DATE = String.valueOf(LocalDateTime.now());
    private static final String REMITTANCE_INFORMATION = "remittance-information";
    private static final String REMITTANCE_INFORMATION_2 = "remittance-information-2";

    @SystemStub
    private EnvironmentVariables environmentVariables = new EnvironmentVariables(
            "ECOMMERCE_FILTER_ENABLED", "true",
            "ENABLE_CART", "true");

    @Mock
    private ExecutionContext context;
    @Mock
    private BizEventToReceiptServiceImpl receiptService;
    @Captor
    private ArgumentCaptor<Receipt> receiptCaptor;
    @Captor
    private ArgumentCaptor<CartForReceipt> cartForReceiptCaptor;
    @Captor
    private ArgumentCaptor<List<Receipt>> receiptBindingCaptor;
    @Captor
    private ArgumentCaptor<List<CartForReceipt>> cartForReceiptBindingCaptor;
    @Spy
    private OutputBinding<List<Receipt>> documentdb;
    @Spy
    private OutputBinding<List<CartForReceipt>> cartDocumentdb;

    @InjectMocks
    private BizEventToReceipt sut;

    @ParameterizedTest
    @SneakyThrows
    @ValueSource(strings = {"1"})
    @NullSource
    void runOkSingleReceipt(String totalNotice) {
        doThrow(ReceiptNotFoundException.class).when(receiptService).getReceipt(EVENT_ID);
        doAnswer(invocation -> {
            EventData passed = invocation.getArgument(2);
            passed.setDebtorFiscalCode(TOKENIZED_DEBTOR_FISCAL_CODE);
            passed.setPayerFiscalCode(TOKENIZED_PAYER_FISCAL_CODE);
            return null;
        }).when(receiptService).tokenizeFiscalCodes(any(), any(Receipt.class), any(EventData.class));
        doReturn(CREATION_DATE).when(receiptService).getTransactionCreationDate(any());

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent(totalNotice));

        // test execution
        assertDoesNotThrow(() -> sut.processBizEventToReceipt(bizEventItems, documentdb, cartDocumentdb, context));

        verify(receiptService).handleSaveReceipt(receiptCaptor.capture());

        Receipt savedReceipt = receiptCaptor.getValue();
        assertNotNull(savedReceipt);
        assertEquals(EVENT_ID, savedReceipt.getEventId());
        assertNotNull(savedReceipt.getEventData());
        assertEquals(CREATION_DATE, savedReceipt.getEventData().getTransactionCreationDate());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, savedReceipt.getEventData().getDebtorFiscalCode());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, savedReceipt.getEventData().getPayerFiscalCode());
        assertNotNull(savedReceipt.getEventData().getCart());
        assertEquals(1, savedReceipt.getEventData().getCart().size());
        assertEquals(REMITTANCE_INFORMATION, savedReceipt.getEventData().getCart().get(0).getSubject());

        verify(receiptService).handleSendMessageToQueue(any(), any());
        verify(receiptService, never()).buildCartForReceipt(any());
        verify(receiptService, never()).saveCartForReceipt(any(), any());
        verify(receiptService, never()).getCartBizEvents(any());
        verify(receiptService, never()).handleSendCartMessageToQueue(anyList(), any());
        verify(documentdb, never()).setValue(any());
        verify(cartDocumentdb, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void runOkPayerInTransactionDetailsSingleReceipt() {
        doThrow(ReceiptNotFoundException.class).when(receiptService).getReceipt(EVENT_ID);
        doReturn(CREATION_DATE).when(receiptService).getTransactionCreationDate(any());
        doAnswer(invocation -> {
            EventData passed = invocation.getArgument(2);
            passed.setDebtorFiscalCode(TOKENIZED_DEBTOR_FISCAL_CODE);
            passed.setPayerFiscalCode(TOKENIZED_PAYER_FISCAL_CODE);
            return null;
        }).when(receiptService).tokenizeFiscalCodes(any(), any(Receipt.class), any(EventData.class));

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEventWithTDetails());

        // test execution
        assertDoesNotThrow(() -> sut.processBizEventToReceipt(bizEventItems, documentdb, cartDocumentdb, context));

        verify(receiptService).handleSaveReceipt(receiptCaptor.capture());

        Receipt savedReceipt = receiptCaptor.getValue();
        assertNotNull(savedReceipt);
        assertEquals(EVENT_ID, savedReceipt.getEventId());
        assertNotNull(savedReceipt.getEventData());
        assertEquals(CREATION_DATE, savedReceipt.getEventData().getTransactionCreationDate());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, savedReceipt.getEventData().getDebtorFiscalCode());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, savedReceipt.getEventData().getPayerFiscalCode());
        assertNotNull(savedReceipt.getEventData().getCart());
        assertEquals(1, savedReceipt.getEventData().getCart().size());
        assertEquals(REMITTANCE_INFORMATION, savedReceipt.getEventData().getCart().get(0).getSubject());

        verify(receiptService).handleSendMessageToQueue(any(), any());
        verify(receiptService, never()).buildCartForReceipt(any());
        verify(receiptService, never()).saveCartForReceipt(any(), any());
        verify(receiptService, never()).getCartBizEvents(any());
        verify(receiptService, never()).handleSendCartMessageToQueue(anyList(), any());
        verify(documentdb, never()).setValue(any());
        verify(cartDocumentdb, never()).setValue(any());
    }

    @Test
    void runDiscardedWithEventNotDONE() {
        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateNotDoneBizEvent());

        // test execution
        assertDoesNotThrow(() -> sut.processBizEventToReceipt(bizEventItems, documentdb, cartDocumentdb, context));

        verify(receiptService, never()).handleSaveReceipt(any());
        verify(receiptService, never()).handleSendMessageToQueue(any(), any());
        verify(receiptService, never()).buildCartForReceipt(any());
        verify(receiptService, never()).saveCartForReceipt(any(), any());
        verify(receiptService, never()).getCartBizEvents(any());
        verify(receiptService, never()).handleSendCartMessageToQueue(anyList(), any());
        verify(documentdb, never()).setValue(any());
        verify(cartDocumentdb, never()).setValue(any());
    }

    @Test
    void runDiscardedWithAnonymousDebtorAndMissingPayer() {
        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateAnonymDebtorBizEvent());
        bizEventItems.get(0).setPayer(null);

        // test execution
        assertDoesNotThrow(() -> sut.processBizEventToReceipt(bizEventItems, documentdb, cartDocumentdb, context));

        verify(receiptService, never()).handleSaveReceipt(any());
        verify(receiptService, never()).handleSendMessageToQueue(any(), any());
        verify(receiptService, never()).buildCartForReceipt(any());
        verify(receiptService, never()).saveCartForReceipt(any(), any());
        verify(receiptService, never()).getCartBizEvents(any());
        verify(receiptService, never()).handleSendCartMessageToQueue(anyList(), any());
        verify(documentdb, never()).setValue(any());
        verify(cartDocumentdb, never()).setValue(any());
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

        // test execution
        assertDoesNotThrow(() -> sut.processBizEventToReceipt(bizEventItems, documentdb, cartDocumentdb, context));

        verify(receiptService, never()).handleSaveReceipt(any());
        verify(receiptService, never()).handleSendMessageToQueue(any(), any());
        verify(receiptService, never()).buildCartForReceipt(any());
        verify(receiptService, never()).saveCartForReceipt(any(), any());
        verify(receiptService, never()).getCartBizEvents(any());
        verify(receiptService, never()).handleSendCartMessageToQueue(anyList(), any());
        verify(documentdb, never()).setValue(any());
        verify(cartDocumentdb, never()).setValue(any());
    }

    @Test
    void runDiscardedWithReceiptAlreadyInserted() throws ReceiptNotFoundException {
        doReturn(new Receipt()).when(receiptService).getReceipt(EVENT_ID);

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent("1"));

        // test execution
        assertDoesNotThrow(() -> sut.processBizEventToReceipt(bizEventItems, documentdb, cartDocumentdb, context));

        verify(receiptService, never()).handleSaveReceipt(any());
        verify(receiptService, never()).handleSendMessageToQueue(any(), any());
        verify(receiptService, never()).buildCartForReceipt(any());
        verify(receiptService, never()).saveCartForReceipt(any(), any());
        verify(receiptService, never()).getCartBizEvents(any());
        verify(receiptService, never()).handleSendCartMessageToQueue(anyList(), any());
        verify(documentdb, never()).setValue(any());
        verify(cartDocumentdb, never()).setValue(any());
    }

    @Test
    void runDiscardedWithCartReceiptAlreadyInserted() throws CartNotFoundException {
        CartForReceipt cart = CartForReceipt.builder()
                .cartId(CART_ID)
                .payload(Payload.builder()
                        .cart(Collections.singletonList(
                                CartPayment.builder()
                                        .bizEventId(EVENT_ID)
                                        .build()
                        ))
                        .build())
                .build();

        doReturn(cart).when(receiptService).getCartForReceipt(CART_ID);

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent("2"));

        // test execution
        assertDoesNotThrow(() -> sut.processBizEventToReceipt(bizEventItems, documentdb, cartDocumentdb, context));

        verify(receiptService, never()).handleSaveReceipt(any());
        verify(receiptService, never()).handleSendMessageToQueue(any(), any());
        verify(receiptService, never()).buildCartForReceipt(any());
        verify(receiptService, never()).saveCartForReceipt(any(), any());
        verify(receiptService, never()).getCartBizEvents(any());
        verify(receiptService, never()).handleSendCartMessageToQueue(anyList(), any());
        verify(documentdb, never()).setValue(any());
        verify(cartDocumentdb, never()).setValue(any());
    }

    @Test
    void runDiscardedWithEventNull() {
        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(null);

        // test execution
        assertDoesNotThrow(() -> sut.processBizEventToReceipt(bizEventItems, documentdb, cartDocumentdb, context));

        verify(receiptService, never()).handleSaveReceipt(any());
        verify(receiptService, never()).handleSendMessageToQueue(any(), any());
        verify(receiptService, never()).buildCartForReceipt(any());
        verify(receiptService, never()).saveCartForReceipt(any(), any());
        verify(receiptService, never()).getCartBizEvents(any());
        verify(receiptService, never()).handleSendCartMessageToQueue(anyList(), any());
        verify(documentdb, never()).setValue(any());
        verify(cartDocumentdb, never()).setValue(any());
    }

    @Test
    void runDiscardedWithCartEventWithInvalidTotalNotice() {
        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent("invalid string"));

        // test execution
        assertThrows(NumberFormatException.class, () -> sut.processBizEventToReceipt(bizEventItems, documentdb, cartDocumentdb, context));

        verify(receiptService, never()).handleSaveReceipt(any());
        verify(receiptService, never()).handleSendMessageToQueue(any(), any());
        verify(receiptService, never()).buildCartForReceipt(any());
        verify(receiptService, never()).saveCartForReceipt(any(), any());
        verify(receiptService, never()).getCartBizEvents(any());
        verify(receiptService, never()).handleSendCartMessageToQueue(anyList(), any());
        verify(documentdb, never()).setValue(any());
        verify(cartDocumentdb, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void errorTokenizingFiscalCodesSingleReceipt() {
        doThrow(ReceiptNotFoundException.class).when(receiptService).getReceipt(EVENT_ID);
        doThrow(new PDVTokenizerException(HTTP_MESSAGE_ERROR, org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR))
                .when(receiptService).tokenizeFiscalCodes(any(), any(), any());

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent("1"));

        // test execution
        assertDoesNotThrow(() -> sut.processBizEventToReceipt(bizEventItems, documentdb, cartDocumentdb, context));

        verify(receiptService, never()).handleSaveReceipt(any());
        verify(receiptService, never()).handleSendMessageToQueue(any(), any());
        verify(receiptService, never()).buildCartForReceipt(any());
        verify(receiptService, never()).saveCartForReceipt(any(), any());
        verify(receiptService, never()).getCartBizEvents(any());
        verify(receiptService, never()).handleSendCartMessageToQueue(anyList(), any());
        verify(documentdb).setValue(receiptBindingCaptor.capture());

        Receipt captured = receiptBindingCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.FAILED, captured.getStatus());
        assertEquals(EVENT_ID, captured.getEventId());
        assertNull(captured.getEventData());

        verify(cartDocumentdb, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void errorSavingReceiptToCosmosSingleReceipt() {
        doThrow(ReceiptNotFoundException.class).when(receiptService).getReceipt(EVENT_ID);
        doAnswer(invocation -> {
            EventData passed = invocation.getArgument(2);
            passed.setDebtorFiscalCode(TOKENIZED_DEBTOR_FISCAL_CODE);
            passed.setPayerFiscalCode(TOKENIZED_PAYER_FISCAL_CODE);
            return null;
        }).when(receiptService).tokenizeFiscalCodes(any(), any(Receipt.class), any(EventData.class));
        doAnswer(invocation -> {
            Receipt passed = invocation.getArgument(0);
            passed.setStatus(ReceiptStatusType.FAILED);
            passed.setReasonErr(ReasonError.builder()
                    .code(ReasonErrorCode.ERROR_COSMOS.getCode())
                    .build());
            return null;
        }).when(receiptService).handleSaveReceipt(any(Receipt.class));

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent("1"));

        // test execution
        assertDoesNotThrow(() -> sut.processBizEventToReceipt(bizEventItems, documentdb, cartDocumentdb, context));

        verify(receiptService).handleSaveReceipt(any());
        verify(receiptService, never()).handleSendMessageToQueue(any(), any());
        verify(receiptService, never()).buildCartForReceipt(any());
        verify(receiptService, never()).saveCartForReceipt(any(), any());
        verify(receiptService, never()).getCartBizEvents(any());
        verify(receiptService, never()).handleSendCartMessageToQueue(anyList(), any());
        verify(documentdb).setValue(receiptBindingCaptor.capture());

        Receipt captured = receiptBindingCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.FAILED, captured.getStatus());
        assertEquals(ReasonErrorCode.ERROR_COSMOS.getCode(), captured.getReasonErr().getCode());
        assertEquals(EVENT_ID, captured.getEventId());
        assertNotNull(captured.getEventData());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, captured.getEventData().getPayerFiscalCode());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, captured.getEventData().getDebtorFiscalCode());
        assertNotNull(captured.getEventData().getCart());
        assertEquals(1, captured.getEventData().getCart().size());

        verify(cartDocumentdb, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void errorAddingMessageToQueueSingleReceipt() {
        doThrow(ReceiptNotFoundException.class).when(receiptService).getReceipt(EVENT_ID);
        doAnswer(invocation -> {
            EventData passed = invocation.getArgument(2);
            passed.setDebtorFiscalCode(TOKENIZED_DEBTOR_FISCAL_CODE);
            passed.setPayerFiscalCode(TOKENIZED_PAYER_FISCAL_CODE);
            return null;
        }).when(receiptService).tokenizeFiscalCodes(any(), any(Receipt.class), any(EventData.class));
        doAnswer(invocation -> {
            Receipt passed = invocation.getArgument(1);
            passed.setStatus(ReceiptStatusType.NOT_QUEUE_SENT);
            passed.setReasonErr(ReasonError.builder()
                    .code(ReasonErrorCode.ERROR_QUEUE.getCode())
                    .build());
            return null;
        }).when(receiptService).handleSendMessageToQueue(any(), any(Receipt.class));


        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent("1"));

        // test execution
        assertDoesNotThrow(() -> sut.processBizEventToReceipt(bizEventItems, documentdb, cartDocumentdb, context));

        verify(receiptService).handleSaveReceipt(any());
        verify(receiptService).handleSendMessageToQueue(any(), any());
        verify(receiptService, never()).buildCartForReceipt(any());
        verify(receiptService, never()).saveCartForReceipt(any(), any());
        verify(receiptService, never()).getCartBizEvents(any());
        verify(receiptService, never()).handleSendCartMessageToQueue(anyList(), any());
        verify(documentdb).setValue(receiptBindingCaptor.capture());

        Receipt captured = receiptBindingCaptor.getValue().get(0);
        assertEquals(ReceiptStatusType.NOT_QUEUE_SENT, captured.getStatus());
        assertEquals(ReasonErrorCode.ERROR_QUEUE.getCode(), captured.getReasonErr().getCode());
        assertEquals(EVENT_ID, captured.getEventId());
        assertNotNull(captured.getEventData());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, captured.getEventData().getPayerFiscalCode());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, captured.getEventData().getDebtorFiscalCode());
        assertNotNull(captured.getEventData().getCart());
        assertEquals(1, captured.getEventData().getCart().size());

        verify(cartDocumentdb, never()).setValue(any());
    }

    @Test
    void bizEventNotProcessedCartNotEnabled() {
        // instantiate here the service to be able to set the environment variable
        environmentVariables.set("ENABLE_CART", "false");
        BizEventToReceiptServiceImpl serviceMock = mock(BizEventToReceiptServiceImpl.class);
        BizEventToReceipt function = new BizEventToReceipt(serviceMock);

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent("2"));

        // test execution
        assertDoesNotThrow(() -> function.processBizEventToReceipt(bizEventItems, documentdb, cartDocumentdb, context));

        verify(receiptService, never()).handleSaveReceipt(any());
        verify(receiptService, never()).handleSendMessageToQueue(any(), any());
        verify(receiptService, never()).buildCartForReceipt(any());
        verify(receiptService, never()).saveCartForReceipt(any(), any());
        verify(receiptService, never()).getCartBizEvents(any());
        verify(receiptService, never()).handleSendCartMessageToQueue(anyList(), any());
        verify(documentdb, never()).setValue(any());
        verify(cartDocumentdb, never()).setValue(any());
    }

    @Test
    void runDiscardedForLegacyCartModel() {
        List<BizEvent> bizEventItems = new ArrayList<>();
        BizEvent bizEvent = generateValidBizEvent(null);
        bizEvent.getTransactionDetails().getTransaction().setAmount(268152);
        bizEvent.getPaymentInfo().setAmount("2681.00");
        bizEventItems.add(bizEvent);

        // test execution
        assertDoesNotThrow(() -> sut.processBizEventToReceipt(bizEventItems, documentdb, cartDocumentdb, context));

        verify(receiptService, never()).handleSaveReceipt(any());
        verify(receiptService, never()).handleSendMessageToQueue(any(), any());
        verify(receiptService, never()).buildCartForReceipt(any());
        verify(receiptService, never()).saveCartForReceipt(any(), any());
        verify(receiptService, never()).getCartBizEvents(any());
        verify(receiptService, never()).handleSendCartMessageToQueue(anyList(), any());
        verify(documentdb, never()).setValue(any());
        verify(cartDocumentdb, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void runOkCartWaiting() {
        CartForReceipt cartForReceipt = buildCartForReceiptWaitingForBiz();
        int totalNotice = cartForReceipt.getPayload().getTotalNotice();

        doThrow(CartNotFoundException.class).when(receiptService).getCartForReceipt(CART_ID);
        doReturn(cartForReceipt).when(receiptService).buildCartForReceipt(any());
        doReturn(cartForReceipt).when(receiptService).saveCartForReceipt(any(), any());

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent(String.valueOf(totalNotice)));

        // test execution
        assertDoesNotThrow(() -> sut.processBizEventToReceipt(bizEventItems, documentdb, cartDocumentdb, context));

        verify(receiptService, never()).handleSaveReceipt(any());
        verify(receiptService, never()).handleSendMessageToQueue(any(), any());
        verify(receiptService).buildCartForReceipt(any());
        verify(receiptService).saveCartForReceipt(cartForReceiptCaptor.capture(), any());

        CartForReceipt savedCart = cartForReceiptCaptor.getValue();

        assertNotNull(savedCart);
        assertEquals(CART_ID, savedCart.getCartId());
        assertEquals(CartStatusType.WAITING_FOR_BIZ_EVENT, savedCart.getStatus());
        assertNotNull(savedCart.getPayload());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, savedCart.getPayload().getPayerFiscalCode());
        assertEquals(CREATION_DATE, savedCart.getPayload().getTransactionCreationDate());
        assertEquals(totalNotice, savedCart.getPayload().getTotalNotice());
        assertNotNull(savedCart.getPayload().getCart());
        assertEquals(1, savedCart.getPayload().getCart().size());
        assertEquals(EVENT_ID, savedCart.getPayload().getCart().get(0).getBizEventId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, savedCart.getPayload().getCart().get(0).getDebtorFiscalCode());
        assertEquals(REMITTANCE_INFORMATION, savedCart.getPayload().getCart().get(0).getSubject());

        verify(receiptService, never()).getCartBizEvents(any());
        verify(receiptService, never()).handleSendCartMessageToQueue(anyList(), any());
        verify(documentdb, never()).setValue(any());
        verify(cartDocumentdb, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void runOkCartCompleted() {
        CartForReceipt cartForReceipt = buildCartForReceiptInserted();
        int totalNotice = cartForReceipt.getPayload().getTotalNotice();

        doThrow(CartNotFoundException.class).when(receiptService).getCartForReceipt(CART_ID);
        doReturn(cartForReceipt).when(receiptService).buildCartForReceipt(any());
        doReturn(cartForReceipt).when(receiptService).saveCartForReceipt(any(), any());

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent(String.valueOf(totalNotice)));

        // test execution
        assertDoesNotThrow(() -> sut.processBizEventToReceipt(bizEventItems, documentdb, cartDocumentdb, context));

        verify(receiptService, never()).handleSaveReceipt(any());
        verify(receiptService, never()).handleSendMessageToQueue(any(), any());
        verify(receiptService).buildCartForReceipt(any());
        verify(receiptService).saveCartForReceipt(cartForReceiptCaptor.capture(), any());

        CartForReceipt savedCart = cartForReceiptCaptor.getValue();

        assertNotNull(savedCart);
        assertEquals(CART_ID, savedCart.getCartId());
        assertEquals(CartStatusType.INSERTED, savedCart.getStatus());
        assertNotNull(savedCart.getPayload());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, savedCart.getPayload().getPayerFiscalCode());
        assertEquals(CREATION_DATE, savedCart.getPayload().getTransactionCreationDate());
        assertEquals(totalNotice, savedCart.getPayload().getTotalNotice());
        assertNotNull(savedCart.getPayload().getCart());
        assertEquals(2, savedCart.getPayload().getCart().size());
        assertEquals(EVENT_ID, savedCart.getPayload().getCart().get(0).getBizEventId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, savedCart.getPayload().getCart().get(0).getDebtorFiscalCode());
        assertEquals(REMITTANCE_INFORMATION, savedCart.getPayload().getCart().get(0).getSubject());
        assertEquals(EVENT_ID_2, savedCart.getPayload().getCart().get(1).getBizEventId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE_2, savedCart.getPayload().getCart().get(1).getDebtorFiscalCode());
        assertEquals(REMITTANCE_INFORMATION_2, savedCart.getPayload().getCart().get(1).getSubject());

        verify(receiptService).getCartBizEvents(any());
        verify(receiptService).handleSendCartMessageToQueue(anyList(), any());
        verify(documentdb, never()).setValue(any());
        verify(cartDocumentdb, never()).setValue(any());
    }

    @Test
    @SneakyThrows
    void errorTokenizingFiscalCodesCart() {
        CartForReceipt cartForReceipt = buildCartForReceiptFailed();
        int totalNotice = cartForReceipt.getPayload().getTotalNotice();

        doThrow(CartNotFoundException.class).when(receiptService).getCartForReceipt(CART_ID);
        doReturn(cartForReceipt).when(receiptService).buildCartForReceipt(any());

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent(String.valueOf(totalNotice)));

        // test execution
        assertDoesNotThrow(() -> sut.processBizEventToReceipt(bizEventItems, documentdb, cartDocumentdb, context));

        verify(receiptService, never()).handleSaveReceipt(any());
        verify(receiptService, never()).handleSendMessageToQueue(any(), any());
        verify(receiptService).buildCartForReceipt(any());
        verify(receiptService, never()).saveCartForReceipt(any(), any());
        verify(receiptService, never()).getCartBizEvents(any());
        verify(receiptService, never()).handleSendCartMessageToQueue(anyList(), any());
        verify(documentdb, never()).setValue(any());
        verify(cartDocumentdb).setValue(cartForReceiptBindingCaptor.capture());

        List<CartForReceipt> savedCartList = cartForReceiptBindingCaptor.getValue();

        assertNotNull(savedCartList);
        assertEquals(1, savedCartList.size());
        CartForReceipt savedCart = savedCartList.get(0);

        assertNotNull(savedCart);
        assertEquals(CART_ID, savedCart.getCartId());
        assertEquals(CartStatusType.FAILED, savedCart.getStatus());
        assertNotNull(savedCart.getPayload());
        assertNull(savedCart.getPayload().getPayerFiscalCode());
        assertNull(savedCart.getPayload().getTransactionCreationDate());
        assertEquals(totalNotice, savedCart.getPayload().getTotalNotice());
        assertNull(savedCart.getPayload().getCart());
        assertNotNull(savedCart.getReasonErr());
        assertEquals(ReasonErrorCode.ERROR_PDV_IO.getCode(), savedCart.getReasonErr().getCode());
    }

    @Test
    @SneakyThrows
    void errorSaveCart() {
        CartForReceipt cartForReceipt = buildCartForReceiptWaitingForBiz();
        int totalNotice = cartForReceipt.getPayload().getTotalNotice();
        CartForReceipt errorSaveCart = buildCartForReceiptWaitingForBiz();
        errorSaveCart.setStatus(CartStatusType.FAILED);
        errorSaveCart.setReasonErr(ReasonError.builder()
                .code(ReasonErrorCode.ERROR_COSMOS.getCode())
                .build());

        doThrow(CartNotFoundException.class).when(receiptService).getCartForReceipt(CART_ID);
        doReturn(cartForReceipt).when(receiptService).buildCartForReceipt(any());
        doReturn(errorSaveCart).when(receiptService).saveCartForReceipt(any(), any());

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent(String.valueOf(totalNotice)));

        // test execution
        assertDoesNotThrow(() -> sut.processBizEventToReceipt(bizEventItems, documentdb, cartDocumentdb, context));

        verify(receiptService, never()).handleSaveReceipt(any());
        verify(receiptService, never()).handleSendMessageToQueue(any(), any());
        verify(receiptService).buildCartForReceipt(any());
        verify(receiptService).saveCartForReceipt(any(), any());
        verify(receiptService, never()).getCartBizEvents(any());
        verify(receiptService, never()).handleSendCartMessageToQueue(anyList(), any());
        verify(documentdb, never()).setValue(any());
        verify(cartDocumentdb).setValue(cartForReceiptBindingCaptor.capture());

        List<CartForReceipt> savedCartList = cartForReceiptBindingCaptor.getValue();

        assertNotNull(savedCartList);
        assertEquals(1, savedCartList.size());
        CartForReceipt savedCart = savedCartList.get(0);

        assertNotNull(savedCart);
        assertEquals(CART_ID, savedCart.getCartId());
        assertEquals(CartStatusType.FAILED, savedCart.getStatus());
        assertNotNull(savedCart.getPayload());
        assertNotNull(savedCart.getPayload());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, savedCart.getPayload().getPayerFiscalCode());
        assertEquals(CREATION_DATE, savedCart.getPayload().getTransactionCreationDate());
        assertEquals(totalNotice, savedCart.getPayload().getTotalNotice());
        assertNotNull(savedCart.getPayload().getCart());
        assertEquals(1, savedCart.getPayload().getCart().size());
        assertEquals(EVENT_ID, savedCart.getPayload().getCart().get(0).getBizEventId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, savedCart.getPayload().getCart().get(0).getDebtorFiscalCode());
        assertEquals(REMITTANCE_INFORMATION, savedCart.getPayload().getCart().get(0).getSubject());
        assertNotNull(savedCart.getReasonErr());
        assertEquals(ReasonErrorCode.ERROR_COSMOS.getCode(), savedCart.getReasonErr().getCode());
    }

    @Test
    @SneakyThrows
    void errorFetchingBizEventForCart() {
        CartForReceipt cartForReceipt = buildCartForReceiptInserted();
        int totalNotice = cartForReceipt.getPayload().getTotalNotice();

        doThrow(CartNotFoundException.class).when(receiptService).getCartForReceipt(CART_ID);
        doReturn(cartForReceipt).when(receiptService).buildCartForReceipt(any());
        doReturn(cartForReceipt).when(receiptService).saveCartForReceipt(any(), any());
        doAnswer(invocation -> {
            CartForReceipt passed = invocation.getArgument(0);
            passed.setStatus(CartStatusType.FAILED);
            passed.setReasonErr(ReasonError.builder()
                    .code(ReasonErrorCode.GENERIC_ERROR.getCode())
                    .build());
            return null;
        }).when(receiptService).getCartBizEvents(any(CartForReceipt.class));

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent(String.valueOf(totalNotice)));

        // test execution
        assertDoesNotThrow(() -> sut.processBizEventToReceipt(bizEventItems, documentdb, cartDocumentdb, context));

        verify(receiptService, never()).handleSaveReceipt(any());
        verify(receiptService, never()).handleSendMessageToQueue(any(), any());
        verify(receiptService).buildCartForReceipt(any());
        verify(receiptService).saveCartForReceipt(any(), any());
        verify(receiptService).getCartBizEvents(any());
        verify(receiptService, never()).handleSendCartMessageToQueue(anyList(), any());
        verify(documentdb, never()).setValue(any());
        verify(cartDocumentdb).setValue(cartForReceiptBindingCaptor.capture());

        List<CartForReceipt> savedCartList = cartForReceiptBindingCaptor.getValue();

        assertNotNull(savedCartList);
        assertEquals(1, savedCartList.size());
        CartForReceipt savedCart = savedCartList.get(0);

        assertNotNull(savedCart);
        assertEquals(CART_ID, savedCart.getCartId());
        assertEquals(CartStatusType.FAILED, savedCart.getStatus());
        assertNotNull(savedCart.getPayload());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, savedCart.getPayload().getPayerFiscalCode());
        assertEquals(CREATION_DATE, savedCart.getPayload().getTransactionCreationDate());
        assertEquals(totalNotice, savedCart.getPayload().getTotalNotice());
        assertNotNull(savedCart.getPayload().getCart());
        assertEquals(2, savedCart.getPayload().getCart().size());
        assertEquals(EVENT_ID, savedCart.getPayload().getCart().get(0).getBizEventId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, savedCart.getPayload().getCart().get(0).getDebtorFiscalCode());
        assertEquals(REMITTANCE_INFORMATION, savedCart.getPayload().getCart().get(0).getSubject());
        assertEquals(EVENT_ID_2, savedCart.getPayload().getCart().get(1).getBizEventId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE_2, savedCart.getPayload().getCart().get(1).getDebtorFiscalCode());
        assertEquals(REMITTANCE_INFORMATION_2, savedCart.getPayload().getCart().get(1).getSubject());
        assertNotNull(savedCart.getReasonErr());
        assertEquals(ReasonErrorCode.GENERIC_ERROR.getCode(), savedCart.getReasonErr().getCode());
    }

    @Test
    @SneakyThrows
    void errorSendOnQueueCart() {
        CartForReceipt cartForReceipt = buildCartForReceiptInserted();
        int totalNotice = cartForReceipt.getPayload().getTotalNotice();

        doThrow(CartNotFoundException.class).when(receiptService).getCartForReceipt(CART_ID);
        doReturn(cartForReceipt).when(receiptService).buildCartForReceipt(any());
        doReturn(cartForReceipt).when(receiptService).saveCartForReceipt(any(), any());
        doAnswer(invocation -> {
            CartForReceipt passed = invocation.getArgument(1);
            passed.setStatus(CartStatusType.NOT_QUEUE_SENT);
            passed.setReasonErr(ReasonError.builder()
                    .code(ReasonErrorCode.ERROR_QUEUE.getCode())
                    .build());
            return null;
        }).when(receiptService).handleSendCartMessageToQueue(anyList(), any(CartForReceipt.class));

        List<BizEvent> bizEventItems = new ArrayList<>();
        bizEventItems.add(generateValidBizEvent(String.valueOf(totalNotice)));

        // test execution
        assertDoesNotThrow(() -> sut.processBizEventToReceipt(bizEventItems, documentdb, cartDocumentdb, context));

        verify(receiptService, never()).handleSaveReceipt(any());
        verify(receiptService, never()).handleSendMessageToQueue(any(), any());
        verify(receiptService).buildCartForReceipt(any());
        verify(receiptService).saveCartForReceipt(any(), any());
        verify(receiptService).getCartBizEvents(any());
        verify(receiptService).handleSendCartMessageToQueue(anyList(), any());
        verify(documentdb, never()).setValue(any());
        verify(cartDocumentdb).setValue(cartForReceiptBindingCaptor.capture());

        List<CartForReceipt> savedCartList = cartForReceiptBindingCaptor.getValue();

        assertNotNull(savedCartList);
        assertEquals(1, savedCartList.size());
        CartForReceipt savedCart = savedCartList.get(0);

        assertNotNull(savedCart);
        assertEquals(CART_ID, savedCart.getCartId());
        assertEquals(CartStatusType.NOT_QUEUE_SENT, savedCart.getStatus());
        assertNotNull(savedCart.getPayload());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, savedCart.getPayload().getPayerFiscalCode());
        assertEquals(CREATION_DATE, savedCart.getPayload().getTransactionCreationDate());
        assertEquals(totalNotice, savedCart.getPayload().getTotalNotice());
        assertNotNull(savedCart.getPayload().getCart());
        assertEquals(2, savedCart.getPayload().getCart().size());
        assertEquals(EVENT_ID, savedCart.getPayload().getCart().get(0).getBizEventId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, savedCart.getPayload().getCart().get(0).getDebtorFiscalCode());
        assertEquals(REMITTANCE_INFORMATION, savedCart.getPayload().getCart().get(0).getSubject());
        assertEquals(EVENT_ID_2, savedCart.getPayload().getCart().get(1).getBizEventId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE_2, savedCart.getPayload().getCart().get(1).getDebtorFiscalCode());
        assertEquals(REMITTANCE_INFORMATION_2, savedCart.getPayload().getCart().get(1).getSubject());
        assertNotNull(savedCart.getReasonErr());
        assertEquals(ReasonErrorCode.ERROR_QUEUE.getCode(), savedCart.getReasonErr().getCode());
    }

    private CartForReceipt buildCartForReceiptWaitingForBiz() {
        return CartForReceipt.builder()
                .cartId(CART_ID)
                .status(CartStatusType.WAITING_FOR_BIZ_EVENT)
                .version("1")
                .payload(Payload.builder()
                        .payerFiscalCode(TOKENIZED_PAYER_FISCAL_CODE)
                        .totalNotice(2)
                        .totalAmount("100.0")
                        .transactionCreationDate(CREATION_DATE)
                        .cart(Collections.singletonList(CartPayment.builder()
                                .bizEventId(EVENT_ID)
                                .amount("50")
                                .debtorFiscalCode(TOKENIZED_DEBTOR_FISCAL_CODE)
                                .payeeName("payee")
                                .subject(REMITTANCE_INFORMATION)
                                .build()))
                        .build())
                .build();
    }

    private CartForReceipt buildCartForReceiptInserted() {
        return CartForReceipt.builder()
                .cartId(CART_ID)
                .status(CartStatusType.INSERTED)
                .version("1")
                .payload(Payload.builder()
                        .payerFiscalCode(TOKENIZED_PAYER_FISCAL_CODE)
                        .totalNotice(2)
                        .totalAmount("100.0")
                        .transactionCreationDate(CREATION_DATE)
                        .cart(List.of(CartPayment.builder()
                                        .bizEventId(EVENT_ID)
                                        .amount("50")
                                        .debtorFiscalCode(TOKENIZED_DEBTOR_FISCAL_CODE)
                                        .payeeName("payee")
                                        .subject(REMITTANCE_INFORMATION)
                                        .build(),
                                CartPayment.builder()
                                        .bizEventId(EVENT_ID_2)
                                        .amount("50")
                                        .debtorFiscalCode(TOKENIZED_DEBTOR_FISCAL_CODE_2)
                                        .payeeName("payee")
                                        .subject(REMITTANCE_INFORMATION_2)
                                        .build()))
                        .build())
                .build();
    }

    private CartForReceipt buildCartForReceiptFailed() {
        return CartForReceipt.builder()
                .cartId(CART_ID)
                .status(CartStatusType.FAILED)
                .version("1")
                .payload(Payload.builder()
                        .totalNotice(2)
                        .build())
                .reasonErr(ReasonError.builder()
                        .code(ReasonErrorCode.ERROR_PDV_IO.getCode())
                        .build())
                .build();
    }

    private BizEvent generateValidBizEvent(String totalNotice) {
        BizEvent item = new BizEvent();

        Payer payer = new Payer();
        payer.setEntityUniqueIdentifierValue(PAYER_FISCAL_CODE);
        Debtor debtor = new Debtor();
        debtor.setEntityUniqueIdentifierValue(DEBTOR_FISCAL_CODE);

        TransactionDetails transactionDetails = new TransactionDetails();
        Transaction transaction = new Transaction();
        transaction.setCreationDate(CREATION_DATE);
        transaction.setOrigin(VALID_IO_CHANNEL);
        transaction.setAmount(10000);
        if ("2".equals(totalNotice)) {
            transaction.setTransactionId(CART_ID);
        }
        transactionDetails.setTransaction(transaction);

        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setTotalNotice(totalNotice);
        paymentInfo.setAmount("100.0");
        paymentInfo.setRemittanceInformation(REMITTANCE_INFORMATION);

        item.setEventStatus(BizEventStatusType.DONE);
        item.setId(EVENT_ID);
        item.setPayer(payer);
        item.setDebtor(debtor);
        item.setTransactionDetails(transactionDetails);
        item.setPaymentInfo(paymentInfo);

        return item;
    }

    private BizEvent generateValidBizEventWithTDetails() {
        BizEvent item = new BizEvent();

        Debtor debtor = new Debtor();
        debtor.setEntityUniqueIdentifierValue(DEBTOR_FISCAL_CODE);

        TransactionDetails transactionDetails = new TransactionDetails();
        Transaction transaction = new Transaction();
        transaction.setCreationDate(CREATION_DATE);
        transaction.setOrigin(VALID_IO_CHANNEL);
        transaction.setAmount(10000);
        transactionDetails.setTransaction(transaction);
        transactionDetails.setUser(User.builder().fiscalCode(PAYER_FISCAL_CODE).build());


        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setTotalNotice("1");
        paymentInfo.setRemittanceInformation(REMITTANCE_INFORMATION);

        item.setEventStatus(BizEventStatusType.DONE);
        item.setId(EVENT_ID);
        item.setDebtor(debtor);
        item.setTransactionDetails(transactionDetails);
        item.setPaymentInfo(paymentInfo);

        return item;
    }

    private BizEvent generateAnonymDebtorBizEvent() {
        BizEvent item = new BizEvent();

        Payer payer = new Payer();
        payer.setEntityUniqueIdentifierValue(PAYER_FISCAL_CODE);
        Debtor debtor = new Debtor();
        debtor.setEntityUniqueIdentifierValue("ANONIMO");

        TransactionDetails transactionDetails = new TransactionDetails();
        Transaction transaction = new Transaction();
        transaction.setCreationDate(CREATION_DATE);
        transaction.setOrigin(VALID_IO_CHANNEL);
        transaction.setAmount(10000);
        transactionDetails.setTransaction(transaction);

        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setTotalNotice("1");
        paymentInfo.setAmount("100");

        item.setEventStatus(BizEventStatusType.DONE);
        item.setId(EVENT_ID);
        item.setPayer(payer);
        item.setDebtor(debtor);
        item.setTransactionDetails(transactionDetails);
        item.setPaymentInfo(paymentInfo);

        return item;
    }

    private BizEvent generateNotDoneBizEvent() {
        BizEvent item = new BizEvent();

        item.setEventStatus(BizEventStatusType.NA);

        return item;
    }
}
