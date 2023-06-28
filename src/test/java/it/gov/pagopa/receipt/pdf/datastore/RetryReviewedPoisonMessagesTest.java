package it.gov.pagopa.receipt.pdf.datastore;

import com.azure.core.http.rest.Response;
import com.azure.storage.queue.models.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptQueueClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.event.BizEvent;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.datastore.utils.ObjectMapperUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryReviewedPoisonMessagesTest {

    @Spy
    private RetryReviewedPoisonMessages function;

    @Mock
    private ExecutionContext context;

    @Captor
    private ArgumentCaptor<String> messageCaptor;

    @Captor
    private ArgumentCaptor<List<ReceiptError>> documentCaptor;

    private final String VALID_CONTENT_TO_RETRY = "{\"id\":\"variant062-a330-4210-9c67-465b7d641aVS\",\"version\":\"2\",\"idPaymentManager\":null,\"complete\":\"false\",\"receiptId\":\"9a9bad2caf604b86a339476373c659b0\",\"missingInfo\":[\"idPaymentManager\",\"psp.pspPartitaIVA\",\"paymentInfo.primaryCiIncurredFee\",\"paymentInfo.idBundle\",\"paymentInfo.idCiBundle\",\"paymentInfo.metadata\"],\"debtorPosition\":{\"modelType\":\"2\",\"noticeNumber\":\"302119891614290410\",\"iuv\":\"02119891614290410\"},\"creditor\":{\"idPA\":\"66666666666\",\"idBrokerPA\":\"66666666666\",\"idStation\":\"66666666666_01\",\"companyName\":\"PA paolo\",\"officeName\":\"office PA\"},\"psp\":{\"idPsp\":\"60000000001\",\"idBrokerPsp\":\"60000000001\",\"idChannel\":\"60000000001_01\",\"psp\":\"PSP Paolo\",\"pspPartitaIVA\":null,\"pspFiscalCode\":\"CF60000000006\",\"channelDescription\":\"app\"},\"debtor\":{\"fullName\":\"John Doe\",\"entityUniqueIdentifierType\":\"F\",\"entityUniqueIdentifierValue\":\"JHNDOE00A01F205N\",\"streetName\":\"street\",\"civicNumber\":\"12\",\"postalCode\":\"89020\",\"city\":\"city\",\"stateProvinceRegion\":\"MI\",\"country\":\"IT\",\"eMail\":\"john.doe@test.it\"},\"payer\":{\"fullName\":\"John Doe\",\"entityUniqueIdentifierType\":\"F\",\"entityUniqueIdentifierValue\":\"JHNDOE00A01F205N\",\"streetName\":\"street\",\"civicNumber\":\"12\",\"postalCode\":\"89020\",\"city\":\"city\",\"stateProvinceRegion\":\"MI\",\"country\":\"IT\",\"eMail\":\"john.doe@test.it\"},\"paymentInfo\":{\"paymentDateTime\":\"2023-04-12T16:21:39.022486\",\"applicationDate\":\"2021-10-01\",\"transferDate\":\"2021-10-02\",\"dueDate\":\"2021-07-31\",\"paymentToken\":\"9a9bad2caf604b86a339476373c659b0\",\"amount\":\"7000\",\"fee\":\"200\",\"primaryCiIncurredFee\":null,\"idBundle\":null,\"idCiBundle\":null,\"totalNotice\":\"1\",\"paymentMethod\":\"creditCard\",\"touchpoint\":\"app\",\"remittanceInformation\":\"TARI 2021\",\"description\":\"TARI 2021\",\"metadata\":null},\"transferList\":[{\"idTransfer\":\"1\",\"fiscalCodePA\":\"77777777777\",\"companyName\":\"Pa Salvo\",\"amount\":\"7000\",\"transferCategory\":\"0101101IM\",\"remittanceInformation\":\"TARI Comune EC_TE\",\"metadata\":null,\"mbdattachment\":null,\"iban\":\"IT96R0123454321000000012345\"}],\"transactionDetails\":null,\"timestamp\":1686919660002,\"properties\":{},\"eventStatus\":\"DONE\",\"eventRetryEnrichmentCount\":0,\"eventTriggeredBySchedule\":false,\"eventErrorMessage\":null}";


    @AfterEach
    public void teardown() throws Exception {
        // reset singleton
        Field instance = ReceiptQueueClientImpl.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    @Test
    public void successfulRun() throws JsonProcessingException {

        ReceiptError receiptError = new ReceiptError();
        receiptError.setMessagePayload(VALID_CONTENT_TO_RETRY);
        receiptError.setStatus(ReceiptErrorStatusType.REVIEWED);
        receiptError.setId("1");

        Logger logger = Logger.getLogger("ManageReceiptPoisonQueue-test-logger");
        when(context.getLogger()).thenReturn(logger);

        ReceiptQueueClientImpl serviceMock = mock(ReceiptQueueClientImpl.class);
        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(201);
        when(serviceMock.sendMessageToQueue(any())).thenReturn(response);

        setMock(serviceMock);
        OutputBinding<List<ReceiptError>> errorToCosmos = (OutputBinding<List<ReceiptError>>)mock(OutputBinding.class);

        assertDoesNotThrow(() -> function.processRetryReviewedPoisonMessages(
                Collections.singletonList(receiptError), errorToCosmos, context));

        verify(serviceMock).sendMessageToQueue(messageCaptor.capture());
        BizEvent captured = ObjectMapperUtils.mapString(new String(Base64.getMimeDecoder()
                .decode(messageCaptor.getValue())), BizEvent.class);
        assertEquals("variant062-a330-4210-9c67-465b7d641aVS", captured.getId());

        verify(errorToCosmos).setValue(documentCaptor.capture());
        ReceiptError documentCaptorValue = documentCaptor.getValue().get(0);
        assertEquals(VALID_CONTENT_TO_RETRY, documentCaptorValue.getMessagePayload());
        assertEquals(ReceiptErrorStatusType.REQUEUED, documentCaptorValue.getStatus());

    }

    @Test
    public void successfulRunWithoutElementToRequeue() {

        ReceiptError receiptError = new ReceiptError();
        receiptError.setMessagePayload(VALID_CONTENT_TO_RETRY);
        receiptError.setStatus(ReceiptErrorStatusType.TO_REVIEW);
        receiptError.setId("1");

        Logger logger = Logger.getLogger("ManageReceiptPoisonQueue-test-logger");
        when(context.getLogger()).thenReturn(logger);

        ReceiptQueueClientImpl serviceMock = mock(ReceiptQueueClientImpl.class);
        setMock(serviceMock);
        OutputBinding<List<ReceiptError>> errorToCosmos = (OutputBinding<List<ReceiptError>>)mock(OutputBinding.class);

        assertDoesNotThrow(() -> function.processRetryReviewedPoisonMessages(
                Collections.singletonList(receiptError), errorToCosmos, context));

        verifyNoInteractions(serviceMock);

    }

    @Test
    public void resendToCosmosIfQueueFailed() throws JsonProcessingException {

        ReceiptError receiptError = new ReceiptError();
        receiptError.setMessagePayload(VALID_CONTENT_TO_RETRY);
        receiptError.setStatus(ReceiptErrorStatusType.REVIEWED);
        receiptError.setId("1");

        Logger logger = Logger.getLogger("ManageReceiptPoisonQueue-test-logger");
        when(context.getLogger()).thenReturn(logger);

        ReceiptQueueClientImpl serviceMock = mock(ReceiptQueueClientImpl.class);
        Response<SendMessageResult> response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(400);
        when(serviceMock.sendMessageToQueue(any())).thenReturn(response);

        setMock(serviceMock);
        OutputBinding<List<ReceiptError>> errorToCosmos = (OutputBinding<List<ReceiptError>>)mock(OutputBinding.class);

        assertDoesNotThrow(() -> function.processRetryReviewedPoisonMessages(
                Collections.singletonList(receiptError), errorToCosmos, context));

        verify(serviceMock).sendMessageToQueue(messageCaptor.capture());
        BizEvent captured = ObjectMapperUtils.mapString(new String(Base64.getMimeDecoder().decode(
                messageCaptor.getValue())), BizEvent.class);
        assertEquals("variant062-a330-4210-9c67-465b7d641aVS", captured.getId());

        verify(errorToCosmos).setValue(documentCaptor.capture());
        ReceiptError documentCaptorValue = documentCaptor.getValue().get(0);
        assertEquals(VALID_CONTENT_TO_RETRY, documentCaptorValue.getMessagePayload());
        assertEquals(ReceiptErrorStatusType.TO_REVIEW, documentCaptorValue.getStatus());
        assertNotNull(documentCaptorValue.getMessageError());

    }

    static void setMock(ReceiptQueueClientImpl mock) {
        try {
            Field instance = ReceiptQueueClientImpl.class.getDeclaredField("instance");
            instance.setAccessible(true);
            instance.set(instance, mock);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}