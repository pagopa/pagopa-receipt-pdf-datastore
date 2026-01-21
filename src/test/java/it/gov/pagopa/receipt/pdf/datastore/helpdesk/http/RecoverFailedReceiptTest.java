package it.gov.pagopa.receipt.pdf.datastore.helpdesk.http;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.CartItem;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventBadRequestException;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventUnprocessableEntityException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.service.HelpdeskService;
import it.gov.pagopa.receipt.pdf.datastore.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.datastore.utils.HttpResponseMessageMock;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecoverFailedReceiptTest {

    private static final String EVENT_ID = "a valid id";

    @Mock
    private ExecutionContext contextMock;
    @Mock
    private ReceiptCosmosService receiptCosmosServiceMock;
    @Mock
    private HttpRequestMessage<Optional<String>> requestMock;
    @Mock
    private HelpdeskService helpdeskServiceMock;

    @Captor
    private ArgumentCaptor<Receipt> receiptCaptor;

    @Spy
    private OutputBinding<Receipt> documentdb;

    @InjectMocks
    private RecoverFailedReceipt sut;

    @BeforeEach
    void setup() {
        doAnswer((Answer<HttpResponseMessage.Builder>) invocation -> {
            HttpStatus status = (HttpStatus) invocation.getArguments()[0];
            return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
        }).when(requestMock).createResponseBuilder(any(HttpStatus.class));
    }

    @ParameterizedTest
    @EnumSource(value = ReceiptStatusType.class, names = {"INSERTED", "NOT_QUEUE_SENT", "FAILED"}, mode = EnumSource.Mode.INCLUDE)
    @SneakyThrows
    void recoverFailedReceiptSuccess(ReceiptStatusType status) {
        Receipt failedReceipt = createFailedReceipt();
        failedReceipt.setStatus(status);
        Receipt recovered = Receipt.builder().status(ReceiptStatusType.INSERTED).build();

        doReturn(failedReceipt).when(receiptCosmosServiceMock).getReceipt(EVENT_ID);
        doReturn(recovered).when(helpdeskServiceMock).recoverFailedReceipt(failedReceipt);

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb, never()).setValue(receiptCaptor.capture());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptFailNoReceiptFound() {
        doThrow(ReceiptNotFoundException.class).when(receiptCosmosServiceMock).getReceipt(EVENT_ID);

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
        assertNotNull(response.getBody());

        verify(helpdeskServiceMock, never()).recoverFailedReceipt(any());
        verify(documentdb, never()).setValue(receiptCaptor.capture());
    }

    @ParameterizedTest
    @EnumSource(value = ReceiptStatusType.class, names = {"INSERTED", "NOT_QUEUE_SENT", "FAILED"}, mode = EnumSource.Mode.EXCLUDE)
    @SneakyThrows
    void recoverFailedReceiptFailReceiptWithUnexpectedStatus(ReceiptStatusType status) {
        Receipt failedReceipt = createFailedReceipt();
        failedReceipt.setStatus(status);

        doReturn(failedReceipt).when(receiptCosmosServiceMock).getReceipt(EVENT_ID);

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatus());
        assertNotNull(response.getBody());

        verify(helpdeskServiceMock, never()).recoverFailedReceipt(any());
        verify(documentdb, never()).setValue(receiptCaptor.capture());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptFailBizEvenUnprocessable() {
        Receipt failedReceipt = createFailedReceipt();

        doReturn(failedReceipt).when(receiptCosmosServiceMock).getReceipt(EVENT_ID);
        doThrow(BizEventUnprocessableEntityException.class).when(helpdeskServiceMock).recoverFailedReceipt(failedReceipt);

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb, never()).setValue(receiptCaptor.capture());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptFailBizEvenNotFound() {
        Receipt failedReceipt = createFailedReceipt();

        doReturn(failedReceipt).when(receiptCosmosServiceMock).getReceipt(EVENT_ID);
        doThrow(BizEventNotFoundException.class).when(helpdeskServiceMock).recoverFailedReceipt(failedReceipt);

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb, never()).setValue(receiptCaptor.capture());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptFailBizEvenInvalid() {
        Receipt failedReceipt = createFailedReceipt();

        doReturn(failedReceipt).when(receiptCosmosServiceMock).getReceipt(EVENT_ID);
        doThrow(BizEventBadRequestException.class).when(helpdeskServiceMock).recoverFailedReceipt(failedReceipt);

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb, never()).setValue(receiptCaptor.capture());
    }

    @Test
    @SneakyThrows
    void recoverFailedReceiptFailRecoverError() {
        Receipt failedReceipt = createFailedReceipt();
        Receipt recovered = Receipt.builder().status(ReceiptStatusType.FAILED).build();

        doReturn(failedReceipt).when(receiptCosmosServiceMock).getReceipt(EVENT_ID);
        doReturn(recovered).when(helpdeskServiceMock).recoverFailedReceipt(failedReceipt);

        // test execution
        HttpResponseMessage response = assertDoesNotThrow(() -> sut.run(requestMock, EVENT_ID, documentdb, contextMock));

        // test assertion
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
        assertNotNull(response.getBody());

        verify(documentdb).setValue(receiptCaptor.capture());
        assertNotNull(receiptCaptor.getValue());
    }

    private Receipt createFailedReceipt() {
        Receipt receipt = new Receipt();

        receipt.setId("a valid id");
        receipt.setEventId("a valid id");

        receipt.setVersion("1");

        receipt.setStatus(ReceiptStatusType.FAILED);
        EventData eventData = new EventData();
        eventData.setDebtorFiscalCode("tokenizedDebtorFiscalCode");
        eventData.setPayerFiscalCode("tokenizedPayerFiscalCode");
        receipt.setEventData(eventData);

        CartItem item = new CartItem();
        List<CartItem> cartItems = Collections.singletonList(item);
        eventData.setCart(cartItems);

        return receipt;
    }
}