package it.gov.pagopa.receipt.pdf.datastore.service.impl;

import com.azure.core.http.rest.Response;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.storage.queue.models.SendMessageResult;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.receipt.pdf.datastore.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.CartQueueClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartPayment;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.Payload;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.Debtor;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.Payer;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.PaymentInfo;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.Transaction;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.TransactionDetails;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.User;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartConcurrentUpdateException;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.service.PDVTokenizerServiceRetryWrapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static it.gov.pagopa.receipt.pdf.datastore.service.impl.BizEventToReceiptServiceImpl.FISCAL_CODE_ANONYMOUS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, SystemStubsExtension.class})
class BizEventToReceiptServiceImplTest {

    private static final String PAYER_FISCAL_CODE = "AAAAAA00A00A000D";
    private static final String DEBTOR_FISCAL_CODE = "AAAAAA00A00A000P";
    private static final String TOKENIZED_DEBTOR_FISCAL_CODE = "tokenizedDebtorFiscalCode";
    private static final String TOKENIZED_PAYER_FISCAL_CODE = "tokenizedPayerFiscalCode";
    private static final String VALID_IO_CHANNEL = "IO";
    private static final String EVENT_ID = "a valid id";
    private static final String EVENT_ID_2 = "a valid id 2";
    private static final String CART_ID = "a valid cart id";
    private static final String CREATION_DATE = String.valueOf(LocalDateTime.now());
    private static final String REMITTANCE_INFORMATION = "remittance-information";

    @Mock
    private ExecutionContext context;
    @Mock
    private PDVTokenizerServiceRetryWrapper pdvTokenizerServiceMock;
    @Mock
    private ReceiptCosmosClientImpl receiptCosmosClient;
    @Mock
    private CartReceiptsCosmosClient cartReceiptsCosmosClient;
    @Mock
    private BizEventCosmosClient bizEventCosmosClientMock;
    @Mock
    private ReceiptQueueClientImpl queueClient;
    @Mock
    private CartQueueClientImpl cartQueueClient;

    @Mock
    private Response<SendMessageResult> queueResponse;
    @Mock
    private CosmosItemResponse<Receipt> cosmosReceiptResponse;
    @Mock
    private CosmosItemResponse<CartForReceipt> cosmosCartResponse;

    @InjectMocks
    private BizEventToReceiptServiceImpl sut;

    @Test
    void run_OK_handleSendMessageToQueue() {
        doReturn(queueResponse).when(queueClient).sendMessageToQueue(anyString());
        doReturn(HttpStatus.CREATED.value()).when(queueResponse).getStatusCode();

        Receipt receipt = new Receipt();

        assertDoesNotThrow(() -> sut.handleSendMessageToQueue(anyList(), receipt));

        assertNotNull(receipt);
        assertNotEquals(ReceiptStatusType.NOT_QUEUE_SENT, receipt.getStatus());
        assertNull(receipt.getReasonErr());
    }

    @Test
    void run_KO_handleSendMessageToQueue() {
        doThrow(new RuntimeException()).when(queueClient).sendMessageToQueue(anyString());

        Receipt receipt = new Receipt();

        assertDoesNotThrow(() -> sut.handleSendMessageToQueue(anyList(), receipt));

        assertNotNull(receipt);
        assertEquals(ReceiptStatusType.NOT_QUEUE_SENT, receipt.getStatus());
        assertNotNull(receipt.getReasonErr());
        assertEquals(ReasonErrorCode.ERROR_QUEUE.getCode(), receipt.getReasonErr().getCode());
        assertNotNull(receipt.getReasonErr().getMessage());
    }

    @Test
    void run_OK_handleSendCartMessageToQueue() {
        doReturn(queueResponse).when(cartQueueClient).sendMessageToQueue(anyString());
        doReturn(HttpStatus.CREATED.value()).when(queueResponse).getStatusCode();

        CartForReceipt cartForReceipt = new CartForReceipt();

        assertDoesNotThrow(() -> sut.handleSendCartMessageToQueue(anyList(), cartForReceipt));

        assertNotNull(cartForReceipt);
        assertNotEquals(CartStatusType.NOT_QUEUE_SENT, cartForReceipt.getStatus());
        assertNull(cartForReceipt.getReasonErr());
    }

    @Test
    void run_KO_handleSendCartMessageToQueue() {
        doThrow(new RuntimeException()).when(cartQueueClient).sendMessageToQueue(anyString());

        CartForReceipt cartForReceipt = new CartForReceipt();

        assertDoesNotThrow(() -> sut.handleSendCartMessageToQueue(anyList(), cartForReceipt));

        assertNotNull(cartForReceipt);
        assertEquals(CartStatusType.NOT_QUEUE_SENT, cartForReceipt.getStatus());
        assertNotNull(cartForReceipt.getReasonErr());
        assertEquals(ReasonErrorCode.ERROR_QUEUE.getCode(), cartForReceipt.getReasonErr().getCode());
        assertNotNull(cartForReceipt.getReasonErr().getMessage());
    }

    @Test
    void run_OK_getReceipt() throws ReceiptNotFoundException {
        doReturn(new Receipt()).when(receiptCosmosClient).getReceiptDocument(anyString());

        Receipt receipt = assertDoesNotThrow(() -> sut.getReceipt(anyString()));

        assertNotNull(receipt);
    }

    @Test
    void run_KO_getReceipt_notFound() throws ReceiptNotFoundException {
        doThrow(ReceiptNotFoundException.class).when(receiptCosmosClient).getReceiptDocument(anyString());

        ReceiptNotFoundException e = assertThrows(ReceiptNotFoundException.class, () -> sut.getReceipt(anyString()));

        assertNotNull(e);
    }

    @Test
    void run_KO_getReceipt_nullDocument() throws ReceiptNotFoundException {
        doReturn(null).when(receiptCosmosClient).getReceiptDocument(anyString());

        ReceiptNotFoundException e = assertThrows(ReceiptNotFoundException.class, () -> sut.getReceipt(anyString()));

        assertNotNull(e);
    }

    @Test
    void run_OK_handleSaveReceipt() {
        doReturn(cosmosReceiptResponse).when(receiptCosmosClient).saveReceipts(any());
        doReturn(HttpStatus.CREATED.value()).when(cosmosReceiptResponse).getStatusCode();

        Receipt receipt = new Receipt();

        assertDoesNotThrow(() -> sut.handleSaveReceipt(receipt));

        assertNotNull(receipt);
        assertEquals(ReceiptStatusType.INSERTED, receipt.getStatus());
        assertTrue(receipt.getInserted_at() > 0);
    }

    @Test
    void run_KO_handleSaveReceipt() {
        doThrow(new RuntimeException()).when(receiptCosmosClient).saveReceipts(any());

        Receipt receipt = new Receipt();

        assertDoesNotThrow(() -> sut.handleSaveReceipt(receipt));

        assertNotNull(receipt);
        assertEquals(ReceiptStatusType.FAILED, receipt.getStatus());
        assertNotNull(receipt.getReasonErr());
        assertEquals(ReasonErrorCode.ERROR_COSMOS.getCode(), receipt.getReasonErr().getCode());
        assertNotNull(receipt.getReasonErr().getMessage());
    }

    @Test
    void run_OK_getTransactionCreationDate_fromTransactionDetails() {
        String date = "date";
        String dateInfo = "dateInfo";
        BizEvent bizEvent = BizEvent.builder()
                .paymentInfo(PaymentInfo.builder()
                        .paymentDateTime(dateInfo)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .transaction(Transaction.builder()
                                .creationDate(date)
                                .build())
                        .build())
                .build();

        String result = assertDoesNotThrow(() -> sut.getTransactionCreationDate(bizEvent));

        assertNotNull(result);
        assertEquals(date, result);
    }

    @Test
    void run_OK_getTransactionCreationDate_fromPaymentInfo() {
        String dateInfo = "dateInfo";
        BizEvent bizEvent = BizEvent.builder()
                .paymentInfo(PaymentInfo.builder()
                        .paymentDateTime(dateInfo)
                        .build())
                .build();

        String result = assertDoesNotThrow(() -> sut.getTransactionCreationDate(bizEvent));

        assertNotNull(result);
        assertEquals(dateInfo, result);
    }

    @Test
    void run_OK_getTransactionCreationDate_notFound() {
        BizEvent bizEvent = new BizEvent();

        String result = assertDoesNotThrow(() -> sut.getTransactionCreationDate(bizEvent));

        assertNull(result);
    }

    @Test
    @SneakyThrows
    void run_OK_tokenizeFiscalCodes() {
        doReturn(TOKENIZED_DEBTOR_FISCAL_CODE).when(pdvTokenizerServiceMock)
                .generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE);
        doReturn(TOKENIZED_PAYER_FISCAL_CODE).when(pdvTokenizerServiceMock)
                .generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE);

        BizEvent bizEvent = BizEvent.builder()
                .debtor(Debtor.builder()
                        .entityUniqueIdentifierValue(DEBTOR_FISCAL_CODE)
                        .build())
                .payer(Payer.builder()
                        .entityUniqueIdentifierValue(PAYER_FISCAL_CODE)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .transaction(Transaction.builder()
                                .origin(VALID_IO_CHANNEL)
                                .build())
                        .build())
                .build();

        Receipt receipt = Receipt.builder()
                .eventData(EventData.builder().build())
                .build();

        assertDoesNotThrow(() -> sut.tokenizeFiscalCodes(bizEvent, receipt, receipt.getEventData()));

        assertNotNull(receipt);
        assertNotNull(receipt.getEventData());
        assertNotEquals(ReceiptStatusType.FAILED, receipt.getStatus());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, receipt.getEventData().getPayerFiscalCode());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, receipt.getEventData().getDebtorFiscalCode());
        assertNull(receipt.getReasonErr());
    }

    @Test
    @SneakyThrows
    void run_OK_tokenizeFiscalCodes_onlyPayerFromUserSection() {
        doReturn(TOKENIZED_PAYER_FISCAL_CODE).when(pdvTokenizerServiceMock)
                .generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE);

        BizEvent bizEvent = BizEvent.builder()
                .transactionDetails(TransactionDetails.builder()
                        .user(User.builder()
                                .fiscalCode(PAYER_FISCAL_CODE)
                                .build())
                        .transaction(Transaction.builder()
                                .origin(VALID_IO_CHANNEL)
                                .build())
                        .build())
                .build();

        Receipt receipt = Receipt.builder()
                .eventData(EventData.builder().build())
                .build();

        assertDoesNotThrow(() -> sut.tokenizeFiscalCodes(bizEvent, receipt, receipt.getEventData()));

        assertNotNull(receipt);
        assertNotNull(receipt.getEventData());
        assertNotEquals(ReceiptStatusType.FAILED, receipt.getStatus());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, receipt.getEventData().getPayerFiscalCode());
        assertEquals(FISCAL_CODE_ANONYMOUS, receipt.getEventData().getDebtorFiscalCode());
        assertNull(receipt.getReasonErr());

        verify(pdvTokenizerServiceMock, never()).generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE);
    }

    @Test
    @SneakyThrows
    void run_OK_tokenizeFiscalCodes_invalidFiscalCodes() {
        BizEvent bizEvent = BizEvent.builder()
                .debtor(Debtor.builder()
                        .entityUniqueIdentifierValue("invalid fiscal code")
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .user(User.builder()
                                .fiscalCode("invalid fiscal code")
                                .build())
                        .transaction(Transaction.builder()
                                .origin(VALID_IO_CHANNEL)
                                .build())
                        .build())
                .build();

        Receipt receipt = Receipt.builder()
                .eventData(EventData.builder().build())
                .build();

        assertDoesNotThrow(() -> sut.tokenizeFiscalCodes(bizEvent, receipt, receipt.getEventData()));

        assertNotNull(receipt);
        assertNotNull(receipt.getEventData());
        assertNotEquals(ReceiptStatusType.FAILED, receipt.getStatus());
        assertNull(receipt.getEventData().getPayerFiscalCode());
        assertEquals(FISCAL_CODE_ANONYMOUS, receipt.getEventData().getDebtorFiscalCode());
        assertNull(receipt.getReasonErr());

        verify(pdvTokenizerServiceMock, never()).generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE);
        verify(pdvTokenizerServiceMock, never()).generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE);
    }

    @Test
    @SneakyThrows
    void run_OK_tokenizeFiscalCodes_invalidOrigin() {
        BizEvent bizEvent = BizEvent.builder()
                .transactionDetails(TransactionDetails.builder()
                        .user(User.builder()
                                .fiscalCode(PAYER_FISCAL_CODE)
                                .build())
                        .transaction(Transaction.builder()
                                .origin("Invalid origin")
                                .build())
                        .build())
                .build();

        Receipt receipt = Receipt.builder()
                .eventData(EventData.builder().build())
                .build();

        assertDoesNotThrow(() -> sut.tokenizeFiscalCodes(bizEvent, receipt, receipt.getEventData()));

        assertNotNull(receipt);
        assertNotNull(receipt.getEventData());
        assertNotEquals(ReceiptStatusType.FAILED, receipt.getStatus());
        assertNull(receipt.getEventData().getPayerFiscalCode());
        assertEquals(FISCAL_CODE_ANONYMOUS, receipt.getEventData().getDebtorFiscalCode());
        assertNull(receipt.getReasonErr());

        verify(pdvTokenizerServiceMock, never()).generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE);
        verify(pdvTokenizerServiceMock, never()).generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE);
    }

    @Test
    @SneakyThrows
    void run_KO_tokenizeFiscalCodes_tokenizerException() {
        String errMsg = "error";
        doThrow(new PDVTokenizerException(errMsg, ReasonErrorCode.ERROR_PDV_IO.getCode()))
                .when(pdvTokenizerServiceMock).generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE);

        BizEvent bizEvent = BizEvent.builder()
                .debtor(Debtor.builder()
                        .entityUniqueIdentifierValue(DEBTOR_FISCAL_CODE)
                        .build())
                .build();

        Receipt receipt = Receipt.builder()
                .eventData(EventData.builder().build())
                .build();

        PDVTokenizerException e = assertThrows(
                PDVTokenizerException.class,
                () -> sut.tokenizeFiscalCodes(bizEvent, receipt, receipt.getEventData())
        );

        assertNotNull(e);
        assertEquals(errMsg, e.getMessage());
        assertEquals(ReasonErrorCode.ERROR_PDV_IO.getCode(), e.getStatusCode());

        assertNotNull(receipt);
        assertNotNull(receipt.getEventData());
        assertEquals(ReceiptStatusType.FAILED, receipt.getStatus());
        assertNull(receipt.getEventData().getPayerFiscalCode());
        assertNull(receipt.getEventData().getDebtorFiscalCode());
        assertNotNull(receipt.getReasonErr());
        assertEquals(ReasonErrorCode.ERROR_PDV_IO.getCode(), receipt.getReasonErr().getCode());
        assertEquals(errMsg, receipt.getReasonErr().getMessage());

        verify(pdvTokenizerServiceMock, never()).generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE);
    }

    @Test
    @SneakyThrows
    void run_OK_buildCartForReceipt_withNoExistingCart() {
        doThrow(CartNotFoundException.class).when(cartReceiptsCosmosClient).getCartItem(CART_ID);
        doReturn(TOKENIZED_DEBTOR_FISCAL_CODE).when(pdvTokenizerServiceMock)
                .generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE);
        doReturn(TOKENIZED_PAYER_FISCAL_CODE).when(pdvTokenizerServiceMock)
                .generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE);

        BizEvent bizEvent = buildValidBizEvent();

        CartForReceipt result = assertDoesNotThrow(() -> sut.buildCartForReceipt(bizEvent));

        assertNotNull(result);
        assertEquals(CART_ID, result.getEventId());
        assertEquals(CartStatusType.WAITING_FOR_BIZ_EVENT, result.getStatus());
        assertNotNull(result.getPayload());
        assertEquals(CREATION_DATE, result.getPayload().getTransactionCreationDate());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, result.getPayload().getPayerFiscalCode());
        assertEquals("2", result.getPayload().getTotalNotice());
        assertEquals("100,00", result.getPayload().getTotalAmount());
        assertNotNull(result.getPayload().getCart());
        assertEquals(1, result.getPayload().getCart().size());
        assertEquals(EVENT_ID, result.getPayload().getCart().get(0).getBizEventId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, result.getPayload().getCart().get(0).getDebtorFiscalCode());
        assertEquals(REMITTANCE_INFORMATION, result.getPayload().getCart().get(0).getSubject());
        assertEquals("40.0", result.getPayload().getCart().get(0).getAmount());
    }

    @Test
    @SneakyThrows
    void run_OK_buildCartForReceipt_withExistingCart() {
        doReturn(buildCartForReceiptWaitingForBiz()).when(cartReceiptsCosmosClient).getCartItem(CART_ID);
        doReturn(TOKENIZED_DEBTOR_FISCAL_CODE).when(pdvTokenizerServiceMock)
                .generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE);

        BizEvent bizEvent = buildValidBizEvent();

        CartForReceipt result = assertDoesNotThrow(() -> sut.buildCartForReceipt(bizEvent));

        assertNotNull(result);
        assertEquals(CART_ID, result.getEventId());
        assertEquals(CartStatusType.INSERTED, result.getStatus());
        assertNotNull(result.getPayload());
        assertEquals(CREATION_DATE, result.getPayload().getTransactionCreationDate());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, result.getPayload().getPayerFiscalCode());
        assertEquals("2", result.getPayload().getTotalNotice());
        assertEquals("100,00", result.getPayload().getTotalAmount());
        assertNotNull(result.getPayload().getCart());
        assertEquals(2, result.getPayload().getCart().size());
        assertEquals(EVENT_ID_2, result.getPayload().getCart().get(0).getBizEventId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, result.getPayload().getCart().get(0).getDebtorFiscalCode());
        assertEquals(REMITTANCE_INFORMATION, result.getPayload().getCart().get(0).getSubject());
        assertEquals("60.0", result.getPayload().getCart().get(0).getAmount());
        assertEquals(EVENT_ID, result.getPayload().getCart().get(1).getBizEventId());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, result.getPayload().getCart().get(1).getDebtorFiscalCode());
        assertEquals(REMITTANCE_INFORMATION, result.getPayload().getCart().get(1).getSubject());
        assertEquals("40.0", result.getPayload().getCart().get(1).getAmount());

        verify(pdvTokenizerServiceMock, never()).generateTokenForFiscalCodeWithRetry(PAYER_FISCAL_CODE);
    }

    @Test
    @SneakyThrows
    void run_KO_buildCartForReceipt_withNoExistingCart_tokenizerException() {
        String errMsg = "error";
        doThrow(CartNotFoundException.class).when(cartReceiptsCosmosClient).getCartItem(CART_ID);
        doThrow(new PDVTokenizerException(errMsg, ReasonErrorCode.ERROR_PDV_IO.getCode()))
                .when(pdvTokenizerServiceMock).generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE);

        BizEvent bizEvent = buildValidBizEvent();

        CartForReceipt result = assertDoesNotThrow(() -> sut.buildCartForReceipt(bizEvent));

        assertNotNull(result);
        assertEquals(CART_ID, result.getEventId());
        assertEquals(CartStatusType.FAILED, result.getStatus());
        assertNull(result.getPayload());
        assertNotNull(result.getReasonErr());
        assertEquals(ReasonErrorCode.ERROR_PDV_IO.getCode(), result.getReasonErr().getCode());
        assertEquals(errMsg, result.getReasonErr().getMessage());
    }

    @Test
    @SneakyThrows
    void run_KO_buildCartForReceipt_withExistingCart_tokenizerException() {
        String errMsg = "error";
        doReturn(buildCartForReceiptWaitingForBiz()).when(cartReceiptsCosmosClient).getCartItem(CART_ID);
        doThrow(new PDVTokenizerException(errMsg, ReasonErrorCode.ERROR_PDV_IO.getCode()))
                .when(pdvTokenizerServiceMock).generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE);

        BizEvent bizEvent = buildValidBizEvent();

        CartForReceipt result = assertDoesNotThrow(() -> sut.buildCartForReceipt(bizEvent));

        assertNotNull(result);
        assertEquals(CART_ID, result.getEventId());
        assertEquals(CartStatusType.FAILED, result.getStatus());
        assertNotNull(result.getPayload());
        assertEquals(CREATION_DATE, result.getPayload().getTransactionCreationDate());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, result.getPayload().getPayerFiscalCode());
        assertEquals("2", result.getPayload().getTotalNotice());
        assertEquals("100,00", result.getPayload().getTotalAmount());
        assertNotNull(result.getReasonErr());
        assertEquals(ReasonErrorCode.ERROR_PDV_IO.getCode(), result.getReasonErr().getCode());
        assertEquals(errMsg, result.getReasonErr().getMessage());
    }

    @Test
    @SneakyThrows
    void run_KO_buildCartForReceipt_withNoExistingCart_genericException() {
        String errMsg = "error";
        doThrow(CartNotFoundException.class).when(cartReceiptsCosmosClient).getCartItem(CART_ID);
        doThrow(new RuntimeException(errMsg)).when(pdvTokenizerServiceMock)
                .generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE);

        BizEvent bizEvent = buildValidBizEvent();

        CartForReceipt result = assertDoesNotThrow(() -> sut.buildCartForReceipt(bizEvent));

        assertNotNull(result);
        assertEquals(CART_ID, result.getEventId());
        assertEquals(CartStatusType.FAILED, result.getStatus());
        assertNull(result.getPayload());
        assertNotNull(result.getReasonErr());
        assertEquals(ReasonErrorCode.GENERIC_ERROR.getCode(), result.getReasonErr().getCode());
        assertEquals(errMsg, result.getReasonErr().getMessage());
    }

    @Test
    @SneakyThrows
    void run_KO_buildCartForReceipt_withExistingCart_genericException() {
        String errMsg = "error";
        doReturn(buildCartForReceiptWaitingForBiz()).when(cartReceiptsCosmosClient).getCartItem(CART_ID);
        doThrow(new RuntimeException(errMsg)).when(pdvTokenizerServiceMock)
                .generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE);

        BizEvent bizEvent = buildValidBizEvent();

        CartForReceipt result = assertDoesNotThrow(() -> sut.buildCartForReceipt(bizEvent));

        assertNotNull(result);
        assertEquals(CART_ID, result.getEventId());
        assertEquals(CartStatusType.FAILED, result.getStatus());
        assertNotNull(result.getPayload());
        assertEquals(CREATION_DATE, result.getPayload().getTransactionCreationDate());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, result.getPayload().getPayerFiscalCode());
        assertEquals("2", result.getPayload().getTotalNotice());
        assertEquals("100,00", result.getPayload().getTotalAmount());
        assertNotNull(result.getReasonErr());
        assertEquals(ReasonErrorCode.GENERIC_ERROR.getCode(), result.getReasonErr().getCode());
        assertEquals(errMsg, result.getReasonErr().getMessage());
    }

    @Test
    @SneakyThrows
    void run_OK_saveCartForReceipt() {
        CartForReceipt cartForReceipt = buildCartForReceiptWaitingForBiz();

        doReturn(cosmosCartResponse).when(cartReceiptsCosmosClient).updateCart(any());
        doReturn(HttpStatus.CREATED.value()).when(cosmosCartResponse).getStatusCode();

        CartForReceipt result = assertDoesNotThrow(() -> sut.saveCartForReceipt(cartForReceipt, any()));

        assertNotNull(result);
        assertNotEquals(CartStatusType.FAILED, result.getStatus());

        verify(pdvTokenizerServiceMock, never()).generateTokenForFiscalCodeWithRetry(anyString());
        verify(cartReceiptsCosmosClient, never()).getCartItem(anyString());
    }

    @Test
    @SneakyThrows
    void run_KO_saveCartForReceipt() {
        CartForReceipt cartForReceipt = buildCartForReceiptWaitingForBiz();

        doThrow(RuntimeException.class).when(cartReceiptsCosmosClient).updateCart(any());

        CartForReceipt result = assertDoesNotThrow(() -> sut.saveCartForReceipt(cartForReceipt, any()));

        assertNotNull(result);
        assertEquals(CartStatusType.FAILED, result.getStatus());
        assertNotNull(result.getReasonErr());
        assertEquals(ReasonErrorCode.ERROR_COSMOS.getCode(), result.getReasonErr().getCode());
        assertNotNull(result.getReasonErr().getMessage());

        verify(pdvTokenizerServiceMock, never()).generateTokenForFiscalCodeWithRetry(anyString());
        verify(cartReceiptsCosmosClient, never()).getCartItem(anyString());
    }

    @Test
    @SneakyThrows
    void run_OK_saveCartForReceipt_afterRetry() {
        CartForReceipt cartForReceipt = buildCartForReceiptWaitingForBiz();

        doThrow(CartConcurrentUpdateException.class)
                .doReturn(cosmosCartResponse).when(cartReceiptsCosmosClient).updateCart(any());
        doReturn(HttpStatus.CREATED.value()).when(cosmosCartResponse).getStatusCode();

        CartForReceipt result = assertDoesNotThrow(() -> sut.saveCartForReceipt(cartForReceipt, buildValidBizEvent()));

        assertNotNull(result);
        assertNotEquals(CartStatusType.FAILED, result.getStatus());

        verify(pdvTokenizerServiceMock, times(2)).generateTokenForFiscalCodeWithRetry(anyString());
        verify(cartReceiptsCosmosClient).getCartItem(anyString());
        verify(cartReceiptsCosmosClient, times(2)).updateCart(any());
    }

    @Test
    @SneakyThrows
    void run_KO_saveCartForReceipt_afterRetryFailOnCartRebuild() {
        CartForReceipt cartForReceipt = buildCartForReceiptWaitingForBiz();
        String errMsg = "error";

        doThrow(CartConcurrentUpdateException.class)
                .when(cartReceiptsCosmosClient).updateCart(any());
        doThrow(new PDVTokenizerException(errMsg, ReasonErrorCode.ERROR_PDV_IO.getCode()))
                .when(pdvTokenizerServiceMock).generateTokenForFiscalCodeWithRetry(DEBTOR_FISCAL_CODE);

        CartForReceipt result = assertDoesNotThrow(() -> sut.saveCartForReceipt(cartForReceipt, buildValidBizEvent()));

        assertNotNull(result);
        assertEquals(CartStatusType.FAILED, result.getStatus());
        assertNotNull(result.getReasonErr());
        assertEquals(ReasonErrorCode.ERROR_PDV_IO.getCode(), result.getReasonErr().getCode());
        assertEquals(errMsg, result.getReasonErr().getMessage());

        verify(pdvTokenizerServiceMock, times(1)).generateTokenForFiscalCodeWithRetry(anyString());
        verify(cartReceiptsCosmosClient).getCartItem(anyString());
    }

    @Test
    @SneakyThrows
    void run_KO_saveCartForReceipt_afterRetryFailAgainOnCosmos() {
        CartForReceipt cartForReceipt = buildCartForReceiptWaitingForBiz();

        doThrow(CartConcurrentUpdateException.class)
                .doThrow(RuntimeException.class)
                .when(cartReceiptsCosmosClient).updateCart(any());

        CartForReceipt result = assertDoesNotThrow(() -> sut.saveCartForReceipt(cartForReceipt, buildValidBizEvent()));

        assertNotNull(result);
        assertEquals(CartStatusType.FAILED, result.getStatus());
        assertNotNull(result.getReasonErr());
        assertEquals(ReasonErrorCode.ERROR_COSMOS.getCode(), result.getReasonErr().getCode());
        assertNotNull(result.getReasonErr().getMessage());

        verify(pdvTokenizerServiceMock, times(2)).generateTokenForFiscalCodeWithRetry(anyString());
        verify(cartReceiptsCosmosClient).getCartItem(anyString());
        verify(cartReceiptsCosmosClient, times(2)).updateCart(any());
    }

    @Test
    void run_OK_getCartBizEvents() throws BizEventNotFoundException {
        when(bizEventCosmosClientMock.getBizEventDocument("1")).thenReturn(new BizEvent());

        assertDoesNotThrow(() -> sut.getCartBizEvents(CartForReceipt.builder()
                .payload(Payload.builder()
                        .cart(List.of(CartPayment.builder()
                                .bizEventId("1")
                                .build()))
                        .build())
                .build()));
    }

    @Test
    void run_KO_getCartBizEvents() throws BizEventNotFoundException {
        doThrow(BizEventNotFoundException.class).when(bizEventCosmosClientMock).getBizEventDocument("1");

        CartForReceipt cartForReceipt = CartForReceipt.builder()
                .payload(Payload.builder()
                        .cart(List.of(CartPayment.builder()
                                .bizEventId("1")
                                .build()))
                        .build())
                .build();

        assertDoesNotThrow(() -> sut.getCartBizEvents(cartForReceipt));

        assertNotNull(cartForReceipt);
        assertEquals(CartStatusType.FAILED, cartForReceipt.getStatus());
        assertNotNull(cartForReceipt.getReasonErr());
        assertEquals(ReasonErrorCode.GENERIC_ERROR.getCode(), cartForReceipt.getReasonErr().getCode());
        assertNotNull(cartForReceipt.getReasonErr().getMessage());
    }

    @Test
    void run_OK_getCartForReceipt() throws CartNotFoundException {
        doReturn(new CartForReceipt()).when(cartReceiptsCosmosClient).getCartItem(anyString());

        CartForReceipt result = assertDoesNotThrow(() -> sut.getCartForReceipt(anyString()));

        assertNotNull(result);
    }

    @Test
    void run_KO_getCartForReceipt_notFound() throws CartNotFoundException {
        doThrow(CartNotFoundException.class).when(cartReceiptsCosmosClient).getCartItem(anyString());

        CartNotFoundException e = assertThrows(CartNotFoundException.class, () -> sut.getCartForReceipt(anyString()));

        assertNotNull(e);
    }

    @Test
    void run_KO_getCartForReceipt_nullDocument() throws CartNotFoundException {
        doReturn(null).when(cartReceiptsCosmosClient).getCartItem(anyString());

        CartNotFoundException e = assertThrows(CartNotFoundException.class, () -> sut.getCartForReceipt(anyString()));

        assertNotNull(e);
    }

    private BizEvent buildValidBizEvent() {
        return BizEvent.builder()
                .id(EVENT_ID)
                .debtor(Debtor.builder()
                        .entityUniqueIdentifierValue(DEBTOR_FISCAL_CODE)
                        .build())
                .payer(Payer.builder()
                        .entityUniqueIdentifierValue(PAYER_FISCAL_CODE)
                        .build())
                .paymentInfo(PaymentInfo.builder()
                        .totalNotice("2")
                        .amount("40.0")
                        .remittanceInformation(REMITTANCE_INFORMATION)
                        .build())
                .transactionDetails(TransactionDetails.builder()
                        .transaction(Transaction.builder()
                                .transactionId(CART_ID)
                                .origin(VALID_IO_CHANNEL)
                                .creationDate(CREATION_DATE)
                                .grandTotal(10000)
                                .build())
                        .build())
                .build();
    }

    private CartForReceipt buildCartForReceiptWaitingForBiz() {
        CartPayment cart = CartPayment.builder()
                .bizEventId(EVENT_ID_2)
                .amount("60.0")
                .debtorFiscalCode(TOKENIZED_DEBTOR_FISCAL_CODE)
                .payeeName("payee")
                .subject(REMITTANCE_INFORMATION)
                .build();

        List<CartPayment> cartList = new ArrayList<>();
        cartList.add(cart);
        return CartForReceipt.builder()
                .eventId(CART_ID)
                .status(CartStatusType.WAITING_FOR_BIZ_EVENT)
                .version("1")
                .payload(Payload.builder()
                        .payerFiscalCode(TOKENIZED_PAYER_FISCAL_CODE)
                        .totalNotice("2")
                        .totalAmount("100,00")
                        .transactionCreationDate(CREATION_DATE)
                        .cart(cartList)
                        .build())
                .build();
    }

}
