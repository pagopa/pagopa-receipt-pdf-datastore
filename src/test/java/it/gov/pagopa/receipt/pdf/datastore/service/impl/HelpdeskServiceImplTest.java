package it.gov.pagopa.receipt.pdf.datastore.service.impl;

import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.models.ModelBridgeInternal;
import it.gov.pagopa.receipt.pdf.datastore.client.BizEventCosmosClient;
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
import it.gov.pagopa.receipt.pdf.datastore.entity.event.enumeration.BizEventStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.CartItem;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventBadRequestException;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventUnprocessableEntityException;
import it.gov.pagopa.receipt.pdf.datastore.exception.PDVTokenizerException;
import it.gov.pagopa.receipt.pdf.datastore.model.MassiveCartRecoverResult;
import it.gov.pagopa.receipt.pdf.datastore.model.MassiveRecoverResult;
import it.gov.pagopa.receipt.pdf.datastore.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.datastore.service.CartReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.datastore.service.ReceiptCosmosService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HelpdeskServiceImplTest {

    private static final String VALID_IO_CHANNEL = "IO";
    private static final String PAYER_FISCAL_CODE = "AAAAAA00A00A000D";
    private static final String DEBTOR_FISCAL_CODE = "AAAAAA00A00A000P";
    private static final String TOKENIZED_DEBTOR_FISCAL_CODE = "tokenizedDebtorFiscalCode";
    private static final String TOKENIZED_PAYER_FISCAL_CODE = "tokenizedPayerFiscalCode";
    private static final String EVENT_ID = "a valid id";
    private static final String CART_ID = "a valid cart id";
    private static final String CREATION_DATE = String.valueOf(LocalDateTime.now());
    private static final String REMITTANCE_INFORMATION = "remittance-information";

    @Mock
    private ReceiptCosmosService receiptCosmosServiceMock;
    @Mock
    private CartReceiptCosmosService cartReceiptCosmosServiceMock;
    @Mock
    private BizEventToReceiptService bizEventToReceiptServiceMock;
    @Mock
    private BizEventCosmosClient bizEventCosmosClientMock;

    @InjectMocks
    @Spy
    private HelpdeskServiceImpl sut;

    @Test
    @SneakyThrows
    void recoverFailedReceipt_OK() {
        doReturn(generateValidBizEvent("1")).when(bizEventCosmosClientMock).getBizEventDocument(anyString());
        doAnswer(invocation -> {
            EventData passed = invocation.getArgument(2);
            passed.setDebtorFiscalCode(TOKENIZED_DEBTOR_FISCAL_CODE);
            passed.setPayerFiscalCode(TOKENIZED_PAYER_FISCAL_CODE);
            return null;
        }).when(bizEventToReceiptServiceMock).tokenizeFiscalCodes(any(), any(Receipt.class), any(EventData.class));
        doReturn(CREATION_DATE).when(bizEventToReceiptServiceMock).getTransactionCreationDate(any());
        doReturn(buildReceipt()).when(bizEventToReceiptServiceMock).updateReceipt(any());

        Receipt result = assertDoesNotThrow(() -> sut.recoverFailedReceipt(Receipt.builder().eventId("id").build()));

        assertNotNull(result);
        assertEquals(EVENT_ID, result.getEventId());
        assertNotNull(result.getEventData());
        assertEquals(CREATION_DATE, result.getEventData().getTransactionCreationDate());
        assertEquals(TOKENIZED_DEBTOR_FISCAL_CODE, result.getEventData().getDebtorFiscalCode());
        assertEquals(TOKENIZED_PAYER_FISCAL_CODE, result.getEventData().getPayerFiscalCode());
        assertNotNull(result.getEventData().getCart());
        assertEquals(1, result.getEventData().getCart().size());
        assertEquals(REMITTANCE_INFORMATION, result.getEventData().getCart().get(0).getSubject());

        verify(bizEventToReceiptServiceMock).handleSendMessageToQueue(anyList(), any());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceipt_KO_BizEventNotFound() {
        doThrow(BizEventNotFoundException.class).when(bizEventCosmosClientMock).getBizEventDocument(anyString());

        assertThrows(BizEventNotFoundException.class, () -> sut.recoverFailedReceipt(Receipt.builder().eventId("id").build()));

        verify(bizEventToReceiptServiceMock, never()).tokenizeFiscalCodes(any(), any(), any());
        verify(bizEventToReceiptServiceMock, never()).getTransactionCreationDate(any());
        verify(bizEventToReceiptServiceMock, never()).updateReceipt(any());
        verify(bizEventToReceiptServiceMock, never()).handleSendMessageToQueue(anyList(), any());
    }

    @ParameterizedTest
    @EnumSource(value = BizEventStatusType.class, names = {"DONE"}, mode = EnumSource.Mode.EXCLUDE)
    @SneakyThrows
    void recoverFailedReceipt_KO_BizEventInvalid_NotDone(BizEventStatusType status) {
        BizEvent bizEvent = generateValidBizEvent("1");
        bizEvent.setEventStatus(status);
        doReturn(bizEvent).when(bizEventCosmosClientMock).getBizEventDocument(anyString());

        assertThrows(BizEventBadRequestException.class, () -> sut.recoverFailedReceipt(Receipt.builder().eventId("id").build()));

        verify(bizEventToReceiptServiceMock, never()).tokenizeFiscalCodes(any(), any(), any());
        verify(bizEventToReceiptServiceMock, never()).getTransactionCreationDate(any());
        verify(bizEventToReceiptServiceMock, never()).updateReceipt(any());
        verify(bizEventToReceiptServiceMock, never()).handleSendMessageToQueue(anyList(), any());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceipt_KO_BizEventInvalid_AnonymousDebtorAndMissingPayer() {
        BizEvent bizEvent = generateAnonymDebtorBizEvent();
        bizEvent.setPayer(null);
        doReturn(bizEvent).when(bizEventCosmosClientMock).getBizEventDocument(anyString());

        assertThrows(BizEventBadRequestException.class, () -> sut.recoverFailedReceipt(Receipt.builder().eventId("id").build()));

        verify(bizEventToReceiptServiceMock, never()).tokenizeFiscalCodes(any(), any(), any());
        verify(bizEventToReceiptServiceMock, never()).getTransactionCreationDate(any());
        verify(bizEventToReceiptServiceMock, never()).updateReceipt(any());
        verify(bizEventToReceiptServiceMock, never()).handleSendMessageToQueue(anyList(), any());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceipt_KO_BizEventInvalid_ECommerceEvent() {
        BizEvent bizEvent = generateValidBizEvent("1");
        bizEvent.getTransactionDetails()
                .setInfo(InfoTransaction.builder()
                        .clientId("CHECKOUT")
                        .build());
        doReturn(bizEvent).when(bizEventCosmosClientMock).getBizEventDocument(anyString());

        assertThrows(BizEventBadRequestException.class, () -> sut.recoverFailedReceipt(Receipt.builder().eventId("id").build()));

        verify(bizEventToReceiptServiceMock, never()).tokenizeFiscalCodes(any(), any(), any());
        verify(bizEventToReceiptServiceMock, never()).getTransactionCreationDate(any());
        verify(bizEventToReceiptServiceMock, never()).updateReceipt(any());
        verify(bizEventToReceiptServiceMock, never()).handleSendMessageToQueue(anyList(), any());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceipt_KO_BizEventInvalid_TotalNoticeNot1() {
        BizEvent bizEvent = generateValidBizEvent("2");
        doReturn(bizEvent).when(bizEventCosmosClientMock).getBizEventDocument(anyString());

        assertThrows(BizEventUnprocessableEntityException.class, () -> sut.recoverFailedReceipt(Receipt.builder().eventId("id").build()));

        verify(bizEventToReceiptServiceMock, never()).tokenizeFiscalCodes(any(), any(), any());
        verify(bizEventToReceiptServiceMock, never()).getTransactionCreationDate(any());
        verify(bizEventToReceiptServiceMock, never()).updateReceipt(any());
        verify(bizEventToReceiptServiceMock, never()).handleSendMessageToQueue(anyList(), any());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceipt_KO_TokenizerError() {
        BizEvent bizEvent = generateValidBizEvent("1");
        doReturn(bizEvent).when(bizEventCosmosClientMock).getBizEventDocument(anyString());
        doThrow(PDVTokenizerException.class)
                .when(bizEventToReceiptServiceMock).tokenizeFiscalCodes(any(), any(Receipt.class), any(EventData.class));

        Receipt result = assertDoesNotThrow(() -> sut.recoverFailedReceipt(Receipt.builder().eventId("id").build()));

        assertNotNull(result);
        assertEquals(ReceiptStatusType.FAILED, result.getStatus());

        verify(bizEventToReceiptServiceMock, never()).getTransactionCreationDate(any());
        verify(bizEventToReceiptServiceMock, never()).handleSaveReceipt(any());
        verify(bizEventToReceiptServiceMock, never()).handleSendMessageToQueue(anyList(), any());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceipt_KO_ErrorSave() {
        Receipt receipt = buildReceipt();
        receipt.setStatus(ReceiptStatusType.FAILED);
        receipt.setReasonErr(ReasonError.builder()
                .code(ReasonErrorCode.ERROR_COSMOS.getCode())
                .build());

        doReturn(generateValidBizEvent("1")).when(bizEventCosmosClientMock).getBizEventDocument(anyString());
        doAnswer(invocation -> {
            EventData passed = invocation.getArgument(2);
            passed.setDebtorFiscalCode(TOKENIZED_DEBTOR_FISCAL_CODE);
            passed.setPayerFiscalCode(TOKENIZED_PAYER_FISCAL_CODE);
            return null;
        }).when(bizEventToReceiptServiceMock).tokenizeFiscalCodes(any(), any(Receipt.class), any(EventData.class));
        doReturn(CREATION_DATE).when(bizEventToReceiptServiceMock).getTransactionCreationDate(any());
        doReturn(receipt).when(bizEventToReceiptServiceMock).updateReceipt(any());

        Receipt result = assertDoesNotThrow(() -> sut.recoverFailedReceipt(Receipt.builder().eventId("id").build()));

        assertNotNull(result);
        assertEquals(ReceiptStatusType.FAILED, result.getStatus());

        verify(bizEventToReceiptServiceMock, never()).handleSendMessageToQueue(anyList(), any());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceipt_KO_ErrorSendOnQueue() {
        doReturn(generateValidBizEvent("1")).when(bizEventCosmosClientMock).getBizEventDocument(anyString());
        doAnswer(invocation -> {
            EventData passed = invocation.getArgument(2);
            passed.setDebtorFiscalCode(TOKENIZED_DEBTOR_FISCAL_CODE);
            passed.setPayerFiscalCode(TOKENIZED_PAYER_FISCAL_CODE);
            return null;
        }).when(bizEventToReceiptServiceMock).tokenizeFiscalCodes(any(), any(Receipt.class), any(EventData.class));
        doReturn(CREATION_DATE).when(bizEventToReceiptServiceMock).getTransactionCreationDate(any());
        doReturn(buildReceipt()).when(bizEventToReceiptServiceMock).updateReceipt(any());
        doAnswer(invocation -> {
            Receipt passed = invocation.getArgument(1);
            passed.setStatus(ReceiptStatusType.NOT_QUEUE_SENT);
            passed.setReasonErr(ReasonError.builder()
                    .code(ReasonErrorCode.ERROR_QUEUE.getCode())
                    .build());
            return null;
        }).when(bizEventToReceiptServiceMock).handleSendMessageToQueue(any(), any(Receipt.class));

        Receipt result = assertDoesNotThrow(() -> sut.recoverFailedReceipt(Receipt.builder().eventId("id").build()));

        assertNotNull(result);
        assertEquals(ReceiptStatusType.NOT_QUEUE_SENT, result.getStatus());
    }

    @Test
    @SneakyThrows
    void recoverFailedCart_OK() {
        List<BizEvent> bizEvents = new ArrayList<>();
        bizEvents.add(generateValidBizEvent("2"));
        bizEvents.add(generateValidBizEvent("2"));

        doReturn(bizEvents).when(bizEventCosmosClientMock).getAllCartBizEventDocument(anyString());
        doReturn(buildCart(CartStatusType.INSERTED)).when(bizEventToReceiptServiceMock).buildCartFromBizEventList(anyList());
        doReturn(buildCart(CartStatusType.INSERTED)).when(bizEventToReceiptServiceMock).saveCartForReceiptWithoutRetry(any());

        CartForReceipt result = assertDoesNotThrow(() -> sut.recoverFailedCart(CartForReceipt.builder().cartId("id").build()));

        assertNotNull(result);
        assertEquals(CartStatusType.INSERTED, result.getStatus());

        verify(bizEventToReceiptServiceMock).handleSendCartMessageToQueue(anyList(), any());
    }

    @Test
    @SneakyThrows
    void recoverFailedCart_KO_BizEventsNotFound() {
        doReturn(Collections.emptyList()).when(bizEventCosmosClientMock).getAllCartBizEventDocument(anyString());

        assertThrows(BizEventBadRequestException.class, () -> sut.recoverFailedCart(CartForReceipt.builder().cartId("id").build()));

        verify(bizEventToReceiptServiceMock, never()).buildCartFromBizEventList(anyList());
        verify(bizEventToReceiptServiceMock, never()).saveCartForReceiptWithoutRetry(any());
        verify(bizEventToReceiptServiceMock, never()).handleSendCartMessageToQueue(anyList(), any());
    }

    @ParameterizedTest
    @EnumSource(value = BizEventStatusType.class, names = {"DONE"}, mode = EnumSource.Mode.EXCLUDE)
    @SneakyThrows
    void recoverFailedCart_KO_BizEventInvalid_NotDone(BizEventStatusType status) {
        BizEvent bizEvent = generateValidBizEvent("2");
        bizEvent.setEventStatus(status);
        doReturn(List.of(bizEvent, generateValidBizEvent("2")))
                .when(bizEventCosmosClientMock).getAllCartBizEventDocument(anyString());

        assertThrows(BizEventBadRequestException.class, () -> sut.recoverFailedCart(CartForReceipt.builder().cartId("id").build()));

        verify(bizEventToReceiptServiceMock, never()).buildCartFromBizEventList(anyList());
        verify(bizEventToReceiptServiceMock, never()).saveCartForReceiptWithoutRetry(any());
        verify(bizEventToReceiptServiceMock, never()).handleSendCartMessageToQueue(anyList(), any());
    }

    @Test
    @SneakyThrows
    void recoverFailedCart_KO_BizEventInvalid_AnonymousDebtorAndMissingPayer() {
        BizEvent bizEvent = generateAnonymDebtorBizEvent();
        bizEvent.setPayer(null);
        doReturn(List.of(bizEvent, generateValidBizEvent("2")))
                .when(bizEventCosmosClientMock).getAllCartBizEventDocument(anyString());

        assertThrows(BizEventBadRequestException.class, () -> sut.recoverFailedCart(CartForReceipt.builder().cartId("id").build()));

        verify(bizEventToReceiptServiceMock, never()).buildCartFromBizEventList(anyList());
        verify(bizEventToReceiptServiceMock, never()).saveCartForReceiptWithoutRetry(any());
        verify(bizEventToReceiptServiceMock, never()).handleSendCartMessageToQueue(anyList(), any());
    }

    @Test
    @SneakyThrows
    void recoverFailedCart_KO_BizEventInvalid_ECommerceEvent() {
        BizEvent bizEvent = generateValidBizEvent("2");
        bizEvent.getTransactionDetails()
                .setInfo(InfoTransaction.builder()
                        .clientId("CHECKOUT")
                        .build());
        doReturn(List.of(bizEvent, generateValidBizEvent("2")))
                .when(bizEventCosmosClientMock).getAllCartBizEventDocument(anyString());

        assertThrows(BizEventBadRequestException.class, () -> sut.recoverFailedCart(CartForReceipt.builder().cartId("id").build()));

        verify(bizEventToReceiptServiceMock, never()).buildCartFromBizEventList(anyList());
        verify(bizEventToReceiptServiceMock, never()).saveCartForReceiptWithoutRetry(any());
        verify(bizEventToReceiptServiceMock, never()).handleSendCartMessageToQueue(anyList(), any());
    }

    @Test
    @SneakyThrows
    void recoverFailedCart_KO_BizEventInvalid_TotalNoticeNotMatching() {
        doReturn(List.of(generateValidBizEvent("2"), generateValidBizEvent("1")))
                .when(bizEventCosmosClientMock).getAllCartBizEventDocument(anyString());

        assertThrows(BizEventUnprocessableEntityException.class, () -> sut.recoverFailedCart(CartForReceipt.builder().cartId("id").build()));

        verify(bizEventToReceiptServiceMock, never()).buildCartFromBizEventList(anyList());
        verify(bizEventToReceiptServiceMock, never()).saveCartForReceiptWithoutRetry(any());
        verify(bizEventToReceiptServiceMock, never()).handleSendCartMessageToQueue(anyList(), any());
    }

    @Test
    @SneakyThrows
    void recoverFailedCart_KO_TokenizerError() {
        List<BizEvent> bizEvents = new ArrayList<>();
        bizEvents.add(generateValidBizEvent("2"));
        bizEvents.add(generateValidBizEvent("2"));

        doReturn(bizEvents).when(bizEventCosmosClientMock).getAllCartBizEventDocument(anyString());
        doThrow(PDVTokenizerException.class).when(bizEventToReceiptServiceMock).buildCartFromBizEventList(anyList());

        PDVTokenizerException e = assertThrows(PDVTokenizerException.class, () -> sut.recoverFailedCart(CartForReceipt.builder().cartId("id").build()));

        assertNotNull(e);

        verify(bizEventToReceiptServiceMock, never()).saveCartForReceiptWithoutRetry(any());
        verify(bizEventToReceiptServiceMock, never()).handleSendCartMessageToQueue(anyList(), any());
    }

    @Test
    @SneakyThrows
    void recoverFailedCart_KO_ErrorSave() {
        List<BizEvent> bizEvents = new ArrayList<>();
        bizEvents.add(generateValidBizEvent("2"));
        bizEvents.add(generateValidBizEvent("2"));

        doReturn(bizEvents).when(bizEventCosmosClientMock).getAllCartBizEventDocument(anyString());
        doReturn(buildCart(CartStatusType.INSERTED)).when(bizEventToReceiptServiceMock).buildCartFromBizEventList(anyList());
        doReturn(buildCart(CartStatusType.FAILED)).when(bizEventToReceiptServiceMock).saveCartForReceiptWithoutRetry(any());

        CartForReceipt result = assertDoesNotThrow(() -> sut.recoverFailedCart(CartForReceipt.builder().cartId("id").build()));

        assertNotNull(result);
        assertEquals(CartStatusType.FAILED, result.getStatus());

        verify(bizEventToReceiptServiceMock, never()).handleSendCartMessageToQueue(anyList(), any());
    }

    @Test
    @SneakyThrows
    void recoverFailedCart_KO_ErrorSendOnQueue() {
        List<BizEvent> bizEvents = new ArrayList<>();
        bizEvents.add(generateValidBizEvent("2"));
        bizEvents.add(generateValidBizEvent("2"));

        doReturn(bizEvents).when(bizEventCosmosClientMock).getAllCartBizEventDocument(anyString());
        doReturn(buildCart(CartStatusType.INSERTED)).when(bizEventToReceiptServiceMock).buildCartFromBizEventList(anyList());
        doReturn(buildCart(CartStatusType.INSERTED)).when(bizEventToReceiptServiceMock).saveCartForReceiptWithoutRetry(any());
        doAnswer(invocation -> {
            CartForReceipt passed = invocation.getArgument(1);
            passed.setStatus(CartStatusType.FAILED);
            return null;
        }).when(bizEventToReceiptServiceMock).handleSendCartMessageToQueue(anyList(), any(CartForReceipt.class));

        CartForReceipt result = assertDoesNotThrow(() -> sut.recoverFailedCart(CartForReceipt.builder().cartId("id").build()));

        assertNotNull(result);
        assertEquals(CartStatusType.FAILED, result.getStatus());
    }

    @Test
    void recoverNoNotifiedReceipt_OK() {
        Receipt receipt = Receipt.builder()
                .status(ReceiptStatusType.FAILED)
                .notificationNumRetry(6)
                .notified_at(24234L)
                .reasonErr(new ReasonError())
                .build();

        Receipt result = assertDoesNotThrow(() -> sut.recoverNoNotifiedReceipt(receipt));

        assertNotNull(result);
        assertEquals(ReceiptStatusType.GENERATED, result.getStatus());
        assertEquals(0, result.getNotificationNumRetry());
        assertEquals(0, result.getNotified_at());
        assertNull(result.getReasonErr());
    }

    @Test
    void recoverNoNotifiedCart_OK() {
        CartForReceipt cart = CartForReceipt.builder()
                .status(CartStatusType.FAILED)
                .payload(Payload.builder()
                        .cart(List.of(
                                CartPayment.builder()
                                        .reasonErrDebtor(new ReasonError())
                                        .build(),
                                CartPayment.builder()
                                        .reasonErrDebtor(new ReasonError())
                                        .build()
                        ))
                        .build())
                .notified_at(24234L)
                .reasonErr(new ReasonError())
                .build();

        CartForReceipt result = assertDoesNotThrow(() -> sut.recoverNoNotifiedCart(cart));

        assertNotNull(result);
        assertEquals(CartStatusType.GENERATED, result.getStatus());
        assertEquals(0, result.getNotificationNumRetry());
        assertEquals(0, result.getNotified_at());
        assertNull(result.getReasonErr());
        assertNotNull(result.getPayload());
        assertNotNull(result.getPayload().getCart());
        result.getPayload().getCart().forEach(cartPayment -> assertNull(cartPayment.getReasonErrDebtor()));
    }

    @ParameterizedTest
    @EnumSource(value = ReceiptStatusType.class, names = {"FAILED", "NOT_QUEUE_SENT", "INSERTED"}, mode = EnumSource.Mode.INCLUDE)
    @SneakyThrows
    void massiveRecoverFailedReceipt_OK(ReceiptStatusType status) {
        doReturn(createIteratorFeedResponse(status))
                .when(receiptCosmosServiceMock).getFailedReceiptByStatus(null, 100, status);
        doReturn(Receipt.builder().status(ReceiptStatusType.INSERTED).build()).when(sut).recoverFailedReceipt(any());

        MassiveRecoverResult result = assertDoesNotThrow(() -> sut.massiveRecoverFailedReceipt(status));

        assertNotNull(result);
        assertEquals(1, result.getSuccessCounter());
        assertEquals(0, result.getErrorCounter());
        assertEquals(0, result.getFailedReceiptList().size());
    }

    @Test
    @SneakyThrows
    void massiveRecoverFailedReceipt_KO_recoverError() {
        doReturn(createIteratorFeedResponse(ReceiptStatusType.FAILED))
                .when(receiptCosmosServiceMock).getFailedReceiptByStatus(null, 100, ReceiptStatusType.FAILED);
        doReturn(Receipt.builder().status(ReceiptStatusType.FAILED).build()).when(sut).recoverFailedReceipt(any());

        MassiveRecoverResult result = assertDoesNotThrow(() -> sut.massiveRecoverFailedReceipt(ReceiptStatusType.FAILED));

        assertNotNull(result);
        assertEquals(0, result.getSuccessCounter());
        assertEquals(1, result.getErrorCounter());
        assertNotNull(result.getFailedReceiptList());
        assertEquals(1, result.getFailedReceiptList().size());
    }

    @Test
    @SneakyThrows
    void massiveRecoverFailedReceipt_KO_recoverThrowException() {
        doReturn(createIteratorFeedResponse(ReceiptStatusType.FAILED))
                .when(receiptCosmosServiceMock).getFailedReceiptByStatus(null, 100, ReceiptStatusType.FAILED);
        doThrow(BizEventBadRequestException.class).when(sut).recoverFailedReceipt(any());

        MassiveRecoverResult result = assertDoesNotThrow(() -> sut.massiveRecoverFailedReceipt(ReceiptStatusType.FAILED));

        assertNotNull(result);
        assertEquals(0, result.getSuccessCounter());
        assertEquals(1, result.getErrorCounter());
        assertEquals(0, result.getFailedReceiptList().size());
    }

    @ParameterizedTest
    @EnumSource(value = CartStatusType.class, names = {"FAILED", "NOT_QUEUE_SENT", "INSERTED"}, mode = EnumSource.Mode.INCLUDE)
    @SneakyThrows
    void massiveRecoverFailedCart_OK(CartStatusType status) {
        doReturn(createIterableFeedResponse(status))
                .when(cartReceiptCosmosServiceMock).getFailedCartReceiptByStatus(null, 100, status);
        doReturn(CartForReceipt.builder().status(CartStatusType.INSERTED).build()).when(sut).recoverFailedCart(any());

        MassiveCartRecoverResult result = assertDoesNotThrow(() -> sut.massiveRecoverFailedCart(status));

        assertNotNull(result);
        assertEquals(1, result.getSuccessCounter());
        assertEquals(0, result.getErrorCounter());
        assertEquals(0, result.getFailedCartList().size());
    }

    @Test
    @SneakyThrows
    void massiveRecoverFailedCart_KO_recoverError() {
        doReturn(createIterableFeedResponse(CartStatusType.FAILED))
                .when(cartReceiptCosmosServiceMock).getFailedCartReceiptByStatus(null, 100, CartStatusType.FAILED);
        doReturn(CartForReceipt.builder().status(CartStatusType.FAILED).build()).when(sut).recoverFailedCart(any());

        MassiveCartRecoverResult result = assertDoesNotThrow(() -> sut.massiveRecoverFailedCart(CartStatusType.FAILED));

        assertNotNull(result);
        assertEquals(0, result.getSuccessCounter());
        assertEquals(1, result.getErrorCounter());
        assertNotNull(result.getFailedCartList());
        assertEquals(1, result.getFailedCartList().size());
    }

    @Test
    @SneakyThrows
    void massiveRecoverFailedCart_KO_recoverThrowException() {
        doReturn(createIterableFeedResponse(CartStatusType.FAILED))
                .when(cartReceiptCosmosServiceMock).getFailedCartReceiptByStatus(null, 100, CartStatusType.FAILED);
        doThrow(BizEventBadRequestException.class).when(sut).recoverFailedCart(any());

        MassiveCartRecoverResult result = assertDoesNotThrow(() -> sut.massiveRecoverFailedCart(CartStatusType.FAILED));

        assertNotNull(result);
        assertEquals(0, result.getSuccessCounter());
        assertEquals(1, result.getErrorCounter());
        assertEquals(0, result.getFailedCartList().size());
    }

    @ParameterizedTest
    @EnumSource(value = ReceiptStatusType.class, names = {"GENERATED", "IO_ERROR_TO_NOTIFY"}, mode = EnumSource.Mode.INCLUDE)
    @SneakyThrows
    void massiveRecoverNoNotifiedReceipt_OK(ReceiptStatusType status) {
        doReturn(createIteratorFeedResponse(status))
                .when(receiptCosmosServiceMock).getNotNotifiedReceiptByStatus(null, 100, status);
        doReturn(Receipt.builder().status(ReceiptStatusType.GENERATED).build()).when(sut).recoverNoNotifiedReceipt(any());

        List<Receipt> result = assertDoesNotThrow(() -> sut.massiveRecoverNoNotifiedReceipt(status));

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @ParameterizedTest
    @EnumSource(value = CartStatusType.class, names = {"GENERATED", "IO_ERROR_TO_NOTIFY"}, mode = EnumSource.Mode.INCLUDE)
    @SneakyThrows
    void massiveRecoverNoNotifiedCart_OK(CartStatusType status) {
        doReturn(createIterableFeedResponse(status))
                .when(cartReceiptCosmosServiceMock).getNotNotifiedCartReceiptByStatus(null, 100, status);
        doReturn(CartForReceipt.builder().status(CartStatusType.GENERATED).build()).when(sut).recoverNoNotifiedCart(any());

        List<CartForReceipt> result = assertDoesNotThrow(() -> sut.massiveRecoverNoNotifiedCart(status));

        assertNotNull(result);
        assertEquals(1, result.size());
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

    private Receipt buildReceipt() {
        return Receipt.builder()
                .id(EVENT_ID)
                .eventId(EVENT_ID)
                .eventData(EventData.builder()
                        .debtorFiscalCode(TOKENIZED_DEBTOR_FISCAL_CODE)
                        .payerFiscalCode(TOKENIZED_PAYER_FISCAL_CODE)
                        .transactionCreationDate(CREATION_DATE)
                        .cart(List.of(
                                CartItem.builder()
                                        .subject(REMITTANCE_INFORMATION)
                                        .build()
                        ))
                        .build())
                .status(ReceiptStatusType.INSERTED)
                .inserted_at(134512343)
                .build();
    }

    private CartForReceipt buildCart(CartStatusType cartStatusType) {
        return CartForReceipt.builder().status(cartStatusType).build();
    }

    private Iterable<FeedResponse<CartForReceipt>> createIterableFeedResponse(CartStatusType failed) {
        return List.of(
                ModelBridgeInternal.createFeedResponse(
                        List.of(CartForReceipt.builder().status(failed).build()),
                        Collections.emptyMap()));
    }

    private List<FeedResponse<Receipt>> createIteratorFeedResponse(ReceiptStatusType failed) {
        return List.of(
                ModelBridgeInternal.createFeedResponse(
                        List.of(Receipt.builder().status(failed).build()),
                        Collections.emptyMap()));
    }
}