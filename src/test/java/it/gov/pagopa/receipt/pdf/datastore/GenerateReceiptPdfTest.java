package it.gov.pagopa.receipt.pdf.datastore;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.PdfEngineClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptBlobClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.EventData;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReasonErrorCode;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.BizEventNotValidException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.model.response.BlobStorageResponse;
import it.gov.pagopa.receipt.pdf.datastore.model.response.PdfEngineResponse;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariable;

@ExtendWith(MockitoExtension.class)
class GenerateReceiptPdfTest {

    private final String BIZ_EVENT_MESSAGE_SAME_CF = "{\"id\":\"variant062-a330-4210-9c67-465b7d641aVS\",\"version\":\"2\",\"idPaymentManager\":null,\"complete\":\"false\",\"receiptId\":\"9a9bad2caf604b86a339476373c659b0\",\"missingInfo\":[\"idPaymentManager\",\"psp.pspPartitaIVA\",\"paymentInfo.primaryCiIncurredFee\",\"paymentInfo.idBundle\",\"paymentInfo.idCiBundle\",\"paymentInfo.metadata\"],\"debtorPosition\":{\"modelType\":\"2\",\"noticeNumber\":\"302119891614290410\",\"iuv\":\"02119891614290410\"},\"creditor\":{\"idPA\":\"66666666666\",\"idBrokerPA\":\"66666666666\",\"idStation\":\"66666666666_01\",\"companyName\":\"PA paolo\",\"officeName\":\"office PA\"},\"psp\":{\"idPsp\":\"60000000001\",\"idBrokerPsp\":\"60000000001\",\"idChannel\":\"60000000001_01\",\"psp\":\"PSP Paolo\",\"pspPartitaIVA\":null,\"pspFiscalCode\":\"CF60000000006\",\"channelDescription\":\"app\"},\"debtor\":{\"fullName\":\"John Doe\",\"entityUniqueIdentifierType\":\"F\",\"entityUniqueIdentifierValue\":\"JHNDOE00A01F205N\",\"streetName\":\"street\",\"civicNumber\":\"12\",\"postalCode\":\"89020\",\"city\":\"city\",\"stateProvinceRegion\":\"MI\",\"country\":\"IT\",\"eMail\":\"john.doe@test.it\"},\"payer\":{\"fullName\":\"John Doe\",\"entityUniqueIdentifierType\":\"F\",\"entityUniqueIdentifierValue\":\"JHNDOE00A01F205N\",\"streetName\":\"street\",\"civicNumber\":\"12\",\"postalCode\":\"89020\",\"city\":\"city\",\"stateProvinceRegion\":\"MI\",\"country\":\"IT\",\"eMail\":\"john.doe@test.it\"},\"paymentInfo\":{\"paymentDateTime\":\"2023-04-12T16:21:39.022486\",\"applicationDate\":\"2021-10-01\",\"transferDate\":\"2021-10-02\",\"dueDate\":\"2021-07-31\",\"paymentToken\":\"9a9bad2caf604b86a339476373c659b0\",\"amount\":\"7000\",\"fee\":\"200\",\"primaryCiIncurredFee\":null,\"idBundle\":null,\"idCiBundle\":null,\"totalNotice\":\"1\",\"paymentMethod\":\"creditCard\",\"touchpoint\":\"app\",\"remittanceInformation\":\"TARI 2021\",\"description\":\"TARI 2021\",\"metadata\":null},\"transferList\":[{\"idTransfer\":\"1\",\"fiscalCodePA\":\"77777777777\",\"companyName\":\"Pa Salvo\",\"amount\":\"7000\",\"transferCategory\":\"0101101IM\",\"remittanceInformation\":\"TARI Comune EC_TE\",\"metadata\":null,\"mbdattachment\":null,\"iban\":\"IT96R0123454321000000012345\"}],\"transactionDetails\":null,\"timestamp\":1686919660002,\"properties\":{},\"eventStatus\":\"DONE\",\"eventRetryEnrichmentCount\":0,\"eventTriggeredBySchedule\":false,\"eventErrorMessage\":null}";
    private final String BIZ_EVENT_MESSAGE_DIFFERENT_CF = "{\"id\":\"variant061-a330-4210-9c67-465b7d641aVS\",\"version\":\"2\",\"idPaymentManager\":null,\"complete\":\"false\",\"receiptId\":\"9a9bad2caf604b86a339476373c659b0\",\"missingInfo\":[\"idPaymentManager\",\"psp.pspPartitaIVA\",\"paymentInfo.primaryCiIncurredFee\",\"paymentInfo.idBundle\",\"paymentInfo.idCiBundle\",\"paymentInfo.metadata\"],\"debtorPosition\":{\"modelType\":\"2\",\"noticeNumber\":\"302119891614290410\",\"iuv\":\"02119891614290410\"},\"creditor\":{\"idPA\":\"66666666666\",\"idBrokerPA\":\"66666666666\",\"idStation\":\"66666666666_01\",\"companyName\":\"PA paolo\",\"officeName\":\"office PA\"},\"psp\":{\"idPsp\":\"60000000001\",\"idBrokerPsp\":\"60000000001\",\"idChannel\":\"60000000001_01\",\"psp\":\"PSP Paolo\",\"pspPartitaIVA\":null,\"pspFiscalCode\":\"CF60000000006\",\"channelDescription\":\"app\"},\"debtor\":{\"fullName\":\"John Doe\",\"entityUniqueIdentifierType\":\"F\",\"entityUniqueIdentifierValue\":\"JHNDOE00A01F205N\",\"streetName\":\"street\",\"civicNumber\":\"12\",\"postalCode\":\"89020\",\"city\":\"city\",\"stateProvinceRegion\":\"MI\",\"country\":\"IT\",\"eMail\":\"john.doe@test.it\"},\"payer\":{\"fullName\":\"Collins Dinners\",\"entityUniqueIdentifierType\":\"F\",\"entityUniqueIdentifierValue\":\"COLDIN00A01F205N\",\"streetName\":\"street\",\"civicNumber\":\"12\",\"postalCode\":\"89020\",\"city\":\"city\",\"stateProvinceRegion\":\"MI\",\"country\":\"IT\",\"eMail\":\"john.doe@test.it\"},\"paymentInfo\":{\"paymentDateTime\":\"2023-04-12T16:21:39.022486\",\"applicationDate\":\"2021-10-01\",\"transferDate\":\"2021-10-02\",\"dueDate\":\"2021-07-31\",\"paymentToken\":\"9a9bad2caf604b86a339476373c659b0\",\"amount\":\"7000\",\"fee\":\"200\",\"primaryCiIncurredFee\":null,\"idBundle\":null,\"idCiBundle\":null,\"totalNotice\":\"1\",\"paymentMethod\":\"creditCard\",\"touchpoint\":\"app\",\"remittanceInformation\":\"TARI 2021\",\"description\":\"TARI 2021\",\"metadata\":null},\"transferList\":[{\"idTransfer\":\"1\",\"fiscalCodePA\":\"77777777777\",\"companyName\":\"Pa Salvo\",\"amount\":\"7000\",\"transferCategory\":\"0101101IM\",\"remittanceInformation\":\"TARI Comune EC_TE\",\"metadata\":null,\"mbdattachment\":null,\"iban\":\"IT96R0123454321000000012345\"}],\"transactionDetails\":null,\"timestamp\":1686919035121,\"properties\":{},\"eventStatus\":\"DONE\",\"eventRetryEnrichmentCount\":0,\"eventTriggeredBySchedule\":false,\"eventErrorMessage\":null}";

    private final String BIZ_EVENT_INVALID_MESSAGE = "invalid message";

    private final String VALID_PAYER_CF = "a valid payer fiscal code";
    private final String VALID_DEBTOR_CF = "a valid debtor fiscal code";
    private final String VALID_BLOB_URL = "a valid debtor blob url";
    private final String VALID_BLOB_NAME = "a valid debtor blob name";

    private final String PDF_ENGINE_ERROR_MESSAGE = "pdf engine error message";

    private static File outputPdfDebtor;
    private static File tempDirectoryDebtor;

    @Spy
    private GenerateReceiptPdf function;

    @Spy
    private Receipt receiptMock;

    @Mock
    private PdfEngineResponse pdfEngineResponse;

    @Mock
    private BlobStorageResponse blobStorageResponse;

    @Mock
    private ExecutionContext context;

    @Captor
    private ArgumentCaptor<List<Receipt>> receiptCaptor;

    @Captor
    private ArgumentCaptor<String> messageCaptor;

    @BeforeEach
    public void createTemp() throws IOException {
        tempDirectoryDebtor = Files.createTempDirectory("temp").toFile();
        outputPdfDebtor = File.createTempFile("outputDebtor", ".tmp", new File("src/test/resources"));
    }

    @AfterEach
    public void teardown() throws IOException, NoSuchFieldException, IllegalAccessException {
        // reset singleton
        tearDownInstance(ReceiptCosmosClientImpl.class);
        tearDownInstance(ReceiptBlobClientImpl.class);
        tearDownInstance(PdfEngineClientImpl.class);

        if(tempDirectoryDebtor.exists()){
            Files.delete(tempDirectoryDebtor.toPath());
        }
        if(outputPdfDebtor.exists()){
            Files.delete(outputPdfDebtor.toPath());
        }
        assertFalse(tempDirectoryDebtor.exists());
        assertFalse(outputPdfDebtor.exists());
    }

    private <T> void tearDownInstance(Class<T> classInstanced) throws IllegalAccessException, NoSuchFieldException {
        Field instance = classInstanced.getDeclaredField("instance");

        instance.setAccessible(true);
        instance.set(null, null);
    }

    @Test
    void runOkReceiptStatusInsertedDifferentFiscalCode() throws ReceiptNotFoundException, IOException {
        ReceiptCosmosClientImpl cosmosClient = mock(ReceiptCosmosClientImpl.class);
        receiptMock.setStatus(ReceiptStatusType.INSERTED);
        EventData eventDataMock = mock(EventData.class);
        when(eventDataMock.getDebtorFiscalCode()).thenReturn(VALID_DEBTOR_CF);
        when(eventDataMock.getPayerFiscalCode()).thenReturn(VALID_PAYER_CF);
        receiptMock.setEventData(eventDataMock);
        when(cosmosClient.getReceiptDocument(any())).thenReturn(receiptMock);

        GenerateReceiptPdfTest.setMock(ReceiptCosmosClientImpl.class, cosmosClient);

        PdfEngineClientImpl pdfEngineClient = mock(PdfEngineClientImpl.class);
        when(pdfEngineResponse.getStatusCode()).thenReturn(HttpStatus.SC_OK);

        File tempDirectoryPayer = Files.createTempDirectory("temp").toFile();
        File outputPdfPayer = File.createTempFile("outputPayer", ".tmp", tempDirectoryPayer);
        when(pdfEngineResponse.getTempDirectoryPath())
                .thenReturn(tempDirectoryDebtor.getAbsolutePath(), tempDirectoryPayer.getAbsolutePath());
        when(pdfEngineResponse.getTempPdfPath())
                .thenReturn(outputPdfDebtor.getAbsolutePath(), outputPdfPayer.getAbsolutePath());

        when(pdfEngineClient.generatePDF(any())).thenReturn(pdfEngineResponse);

        GenerateReceiptPdfTest.setMock(PdfEngineClientImpl.class, pdfEngineClient);

        ReceiptBlobClientImpl blobClient = mock(ReceiptBlobClientImpl.class);
        when(blobStorageResponse.getStatusCode()).thenReturn(com.microsoft.azure.functions.HttpStatus.CREATED.value());
        when(blobStorageResponse.getDocumentUrl()).thenReturn(VALID_BLOB_URL);
        when(blobStorageResponse.getDocumentName()).thenReturn(VALID_BLOB_NAME);
        when(blobClient.savePdfToBlobStorage(any(), anyString())).thenReturn(blobStorageResponse);

        GenerateReceiptPdfTest.setMock(ReceiptBlobClientImpl.class, blobClient);

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        @SuppressWarnings("unchecked")
        OutputBinding<String> requeueMessage = (OutputBinding<String>) spy(OutputBinding.class);

        // test execution
        assertDoesNotThrow(() -> function.processGenerateReceipt(BIZ_EVENT_MESSAGE_DIFFERENT_CF, documentdb, requeueMessage, context));

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt capturedCosmos = receiptCaptor.getValue().get(0);

        assertEquals(ReceiptStatusType.GENERATED, capturedCosmos.getStatus());
        assertEquals(VALID_DEBTOR_CF, capturedCosmos.getEventData().getDebtorFiscalCode());
        assertEquals(VALID_PAYER_CF, capturedCosmos.getEventData().getPayerFiscalCode());
        assertEquals(VALID_BLOB_URL, capturedCosmos.getMdAttach().getUrl());
        assertEquals(VALID_BLOB_NAME, capturedCosmos.getMdAttach().getName());
        assertEquals(VALID_BLOB_URL, capturedCosmos.getMdAttachPayer().getUrl());
        assertEquals(VALID_BLOB_NAME, capturedCosmos.getMdAttachPayer().getName());

        assertFalse(tempDirectoryPayer.exists());
        assertFalse(outputPdfPayer.exists());
    }

    @Test
    void runOkReceiptStatusInsertedSameFiscalCode() throws ReceiptNotFoundException {
        ReceiptCosmosClientImpl cosmosClient = mock(ReceiptCosmosClientImpl.class);
        receiptMock.setStatus(ReceiptStatusType.INSERTED);
        EventData eventDataMock = mock(EventData.class);
        when(eventDataMock.getDebtorFiscalCode()).thenReturn(VALID_DEBTOR_CF);
        when(eventDataMock.getPayerFiscalCode()).thenReturn(VALID_DEBTOR_CF);
        receiptMock.setEventData(eventDataMock);
        when(cosmosClient.getReceiptDocument(any())).thenReturn(receiptMock);

        GenerateReceiptPdfTest.setMock(ReceiptCosmosClientImpl.class, cosmosClient);

        PdfEngineClientImpl pdfEngineClient = mock(PdfEngineClientImpl.class);
        when(pdfEngineResponse.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        when(pdfEngineResponse.getTempDirectoryPath()).thenReturn(tempDirectoryDebtor.getAbsolutePath());
        when(pdfEngineResponse.getTempPdfPath()).thenReturn(outputPdfDebtor.getAbsolutePath());

        when(pdfEngineClient.generatePDF(any())).thenReturn(pdfEngineResponse);

        GenerateReceiptPdfTest.setMock(PdfEngineClientImpl.class, pdfEngineClient);

        ReceiptBlobClientImpl blobClient = mock(ReceiptBlobClientImpl.class);
        when(blobStorageResponse.getStatusCode()).thenReturn(com.microsoft.azure.functions.HttpStatus.CREATED.value());
        when(blobStorageResponse.getDocumentUrl()).thenReturn(VALID_BLOB_URL);
        when(blobStorageResponse.getDocumentName()).thenReturn(VALID_BLOB_NAME);
        when(blobClient.savePdfToBlobStorage(any(), anyString())).thenReturn(blobStorageResponse);

        GenerateReceiptPdfTest.setMock(ReceiptBlobClientImpl.class, blobClient);

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        @SuppressWarnings("unchecked")
        OutputBinding<String> requeueMessage = (OutputBinding<String>) spy(OutputBinding.class);

        // test execution
        assertDoesNotThrow(() -> function.processGenerateReceipt(BIZ_EVENT_MESSAGE_SAME_CF, documentdb, requeueMessage, context));

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt capturedCosmos = receiptCaptor.getValue().get(0);

        assertEquals(ReceiptStatusType.GENERATED, capturedCosmos.getStatus());
        assertEquals(VALID_BLOB_URL, capturedCosmos.getMdAttach().getUrl());
        assertEquals(VALID_BLOB_NAME, capturedCosmos.getMdAttach().getName());
        assertTrue(capturedCosmos.getMdAttachPayer() == null ||
                capturedCosmos.getMdAttachPayer().getUrl() == null ||
                capturedCosmos.getMdAttachPayer().getUrl().isEmpty()
        );
    }

    @Test
    void runOkReceiptStatusRetrySameFiscalCode() throws ReceiptNotFoundException {
        ReceiptCosmosClientImpl cosmosClient = mock(ReceiptCosmosClientImpl.class);
        receiptMock.setStatus(ReceiptStatusType.RETRY);
        EventData eventDataMock = mock(EventData.class);
        when(eventDataMock.getDebtorFiscalCode()).thenReturn(VALID_DEBTOR_CF);
        when(eventDataMock.getPayerFiscalCode()).thenReturn(VALID_DEBTOR_CF);
        receiptMock.setEventData(eventDataMock);
        when(cosmosClient.getReceiptDocument(any())).thenReturn(receiptMock);

        GenerateReceiptPdfTest.setMock(ReceiptCosmosClientImpl.class, cosmosClient);

        PdfEngineClientImpl pdfEngineClient = mock(PdfEngineClientImpl.class);
        when(pdfEngineResponse.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        when(pdfEngineResponse.getTempDirectoryPath()).thenReturn(tempDirectoryDebtor.getAbsolutePath());
        when(pdfEngineResponse.getTempPdfPath()).thenReturn(outputPdfDebtor.getAbsolutePath());

        when(pdfEngineClient.generatePDF(any())).thenReturn(pdfEngineResponse);

        GenerateReceiptPdfTest.setMock(PdfEngineClientImpl.class, pdfEngineClient);

        ReceiptBlobClientImpl blobClient = mock(ReceiptBlobClientImpl.class);
        when(blobStorageResponse.getStatusCode()).thenReturn(com.microsoft.azure.functions.HttpStatus.CREATED.value());
        when(blobStorageResponse.getDocumentUrl()).thenReturn(VALID_BLOB_URL);
        when(blobStorageResponse.getDocumentName()).thenReturn(VALID_BLOB_NAME);
        when(blobClient.savePdfToBlobStorage(any(), anyString())).thenReturn(blobStorageResponse);

        GenerateReceiptPdfTest.setMock(ReceiptBlobClientImpl.class, blobClient);

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        @SuppressWarnings("unchecked")
        OutputBinding<String> requeueMessage = (OutputBinding<String>) spy(OutputBinding.class);

        // test execution
        assertDoesNotThrow(() -> function.processGenerateReceipt(BIZ_EVENT_MESSAGE_SAME_CF, documentdb, requeueMessage, context));

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt capturedCosmos = receiptCaptor.getValue().get(0);

        assertEquals(ReceiptStatusType.GENERATED, capturedCosmos.getStatus());
        assertEquals(VALID_BLOB_URL, capturedCosmos.getMdAttach().getUrl());
        assertEquals(VALID_BLOB_NAME, capturedCosmos.getMdAttach().getName());
        assertTrue(capturedCosmos.getMdAttachPayer() == null ||
                capturedCosmos.getMdAttachPayer().getUrl() == null ||
                capturedCosmos.getMdAttachPayer().getUrl().isEmpty()
        );
    }

    @Test
    void runDiscarded() throws ReceiptNotFoundException {
        ReceiptCosmosClientImpl cosmosClient = mock(ReceiptCosmosClientImpl.class);
        receiptMock.setStatus(ReceiptStatusType.NOT_QUEUE_SENT);
        EventData eventDataMock = mock(EventData.class);

        receiptMock.setEventData(eventDataMock);
        when(cosmosClient.getReceiptDocument(any())).thenReturn(receiptMock);

        GenerateReceiptPdfTest.setMock(ReceiptCosmosClientImpl.class, cosmosClient);

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        @SuppressWarnings("unchecked")
        OutputBinding<String> requeueMessage = (OutputBinding<String>) spy(OutputBinding.class);

        // test execution
        assertDoesNotThrow(() -> function.processGenerateReceipt(BIZ_EVENT_MESSAGE_SAME_CF, documentdb, requeueMessage, context));

        verify(documentdb, never()).setValue(any());
    }

    @Test
    void runReceiptNotFound() throws ReceiptNotFoundException {
        ReceiptCosmosClientImpl cosmosClient = mock(ReceiptCosmosClientImpl.class);
        when(cosmosClient.getReceiptDocument(any()))
                .thenThrow(ReceiptNotFoundException.class);
        GenerateReceiptPdfTest.setMock(ReceiptCosmosClientImpl.class, cosmosClient);

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        @SuppressWarnings("unchecked")
        OutputBinding<String> requeueMessage = (OutputBinding<String>) spy(OutputBinding.class);

        // test execution
        Assertions.assertThrows(ReceiptNotFoundException.class,
                () -> function.processGenerateReceipt(BIZ_EVENT_MESSAGE_SAME_CF, documentdb, requeueMessage, context));
    }

    @Test
    void runKoInvalidBizEventMessage() {
        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        @SuppressWarnings("unchecked")
        OutputBinding<String> requeueMessage = (OutputBinding<String>) spy(OutputBinding.class);

        assertThrows(BizEventNotValidException.class, () -> function.processGenerateReceipt(BIZ_EVENT_INVALID_MESSAGE, documentdb, requeueMessage, context));
    }

    @Test
    void runKoPdfEngine400() throws ReceiptNotFoundException {
        ReceiptCosmosClientImpl cosmosClient = mock(ReceiptCosmosClientImpl.class);
        receiptMock.setStatus(ReceiptStatusType.INSERTED);
        EventData eventDataMock = mock(EventData.class);
        when(eventDataMock.getDebtorFiscalCode()).thenReturn(VALID_DEBTOR_CF);
        when(eventDataMock.getPayerFiscalCode()).thenReturn(VALID_DEBTOR_CF);
        receiptMock.setEventData(eventDataMock);
        receiptMock.setNumRetry(0);
        when(cosmosClient.getReceiptDocument(any())).thenReturn(receiptMock);

        GenerateReceiptPdfTest.setMock(ReceiptCosmosClientImpl.class, cosmosClient);

        PdfEngineClientImpl pdfEngineClient = mock(PdfEngineClientImpl.class);
        when(pdfEngineResponse.getStatusCode()).thenReturn(HttpStatus.SC_BAD_REQUEST);
        when(pdfEngineResponse.getErrorMessage()).thenReturn(PDF_ENGINE_ERROR_MESSAGE);
        when(pdfEngineClient.generatePDF(any())).thenReturn(pdfEngineResponse);

        GenerateReceiptPdfTest.setMock(PdfEngineClientImpl.class, pdfEngineClient);

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        @SuppressWarnings("unchecked")
        OutputBinding<String> requeueMessage = (OutputBinding<String>) spy(OutputBinding.class);

        // test execution
        assertDoesNotThrow(() -> function.processGenerateReceipt(BIZ_EVENT_MESSAGE_SAME_CF, documentdb, requeueMessage, context));

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt capturedCosmos = receiptCaptor.getValue().get(0);

        verify(requeueMessage).setValue(messageCaptor.capture());
        String caputuredMessage = messageCaptor.getValue();

        assertEquals(BIZ_EVENT_MESSAGE_SAME_CF, caputuredMessage);
        assertEquals(ReceiptStatusType.RETRY, capturedCosmos.getStatus());
        assertEquals(PDF_ENGINE_ERROR_MESSAGE, capturedCosmos.getReasonErr().getMessage());

    }

    @Test
    void runKoPdfEngine401() throws ReceiptNotFoundException {
        ReceiptCosmosClientImpl cosmosClient = mock(ReceiptCosmosClientImpl.class);
        receiptMock.setStatus(ReceiptStatusType.INSERTED);
        EventData eventDataMock = mock(EventData.class);
        when(eventDataMock.getDebtorFiscalCode()).thenReturn(VALID_DEBTOR_CF);
        when(eventDataMock.getPayerFiscalCode()).thenReturn(VALID_DEBTOR_CF);
        receiptMock.setEventData(eventDataMock);
        receiptMock.setNumRetry(0);
        when(cosmosClient.getReceiptDocument(any())).thenReturn(receiptMock);

        GenerateReceiptPdfTest.setMock(ReceiptCosmosClientImpl.class, cosmosClient);

        PdfEngineClientImpl pdfEngineClient = mock(PdfEngineClientImpl.class);
        when(pdfEngineResponse.getStatusCode()).thenReturn(com.microsoft.azure.functions.HttpStatus.UNAUTHORIZED.value());
        when(pdfEngineResponse.getErrorMessage()).thenReturn(PDF_ENGINE_ERROR_MESSAGE);
        when(pdfEngineClient.generatePDF(any())).thenReturn(pdfEngineResponse);

        GenerateReceiptPdfTest.setMock(PdfEngineClientImpl.class, pdfEngineClient);

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        @SuppressWarnings("unchecked")
        OutputBinding<String> requeueMessage = (OutputBinding<String>) spy(OutputBinding.class);

        // test execution
        assertDoesNotThrow(() -> function.processGenerateReceipt(BIZ_EVENT_MESSAGE_SAME_CF, documentdb, requeueMessage, context));

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt capturedCosmos = receiptCaptor.getValue().get(0);

        verify(requeueMessage).setValue(messageCaptor.capture());
        String caputuredMessage = messageCaptor.getValue();

        assertEquals(BIZ_EVENT_MESSAGE_SAME_CF, caputuredMessage);
        assertEquals(ReceiptStatusType.RETRY, capturedCosmos.getStatus());
        assertEquals(ReasonErrorCode.ERROR_PDF_ENGINE.getCustomCode(com.microsoft.azure.functions.HttpStatus.UNAUTHORIZED.value()), capturedCosmos.getReasonErr().getCode());
        assertEquals(PDF_ENGINE_ERROR_MESSAGE, capturedCosmos.getReasonErr().getMessage());
    }

    @Test
    void runKoPdfEngine500() throws ReceiptNotFoundException {
        ReceiptCosmosClientImpl cosmosClient = mock(ReceiptCosmosClientImpl.class);
        receiptMock.setStatus(ReceiptStatusType.INSERTED);
        EventData eventDataMock = mock(EventData.class);
        when(eventDataMock.getDebtorFiscalCode()).thenReturn(VALID_DEBTOR_CF);
        when(eventDataMock.getPayerFiscalCode()).thenReturn(VALID_DEBTOR_CF);
        receiptMock.setEventData(eventDataMock);
        receiptMock.setNumRetry(0);
        when(cosmosClient.getReceiptDocument(any())).thenReturn(receiptMock);

        GenerateReceiptPdfTest.setMock(ReceiptCosmosClientImpl.class, cosmosClient);

        PdfEngineClientImpl pdfEngineClient = mock(PdfEngineClientImpl.class);
        when(pdfEngineResponse.getStatusCode()).thenReturn(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        when(pdfEngineResponse.getErrorMessage()).thenReturn(PDF_ENGINE_ERROR_MESSAGE);
        when(pdfEngineClient.generatePDF(any())).thenReturn(pdfEngineResponse);

        GenerateReceiptPdfTest.setMock(PdfEngineClientImpl.class, pdfEngineClient);

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        @SuppressWarnings("unchecked")
        OutputBinding<String> requeueMessage = (OutputBinding<String>) spy(OutputBinding.class);

        // test execution
        assertDoesNotThrow(() -> function.processGenerateReceipt(BIZ_EVENT_MESSAGE_SAME_CF, documentdb, requeueMessage, context));

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt capturedCosmos = receiptCaptor.getValue().get(0);

        verify(requeueMessage).setValue(messageCaptor.capture());
        String caputuredMessage = messageCaptor.getValue();

        assertEquals(BIZ_EVENT_MESSAGE_SAME_CF, caputuredMessage);
        assertEquals(ReceiptStatusType.RETRY, capturedCosmos.getStatus());
        assertEquals(ReasonErrorCode.ERROR_PDF_ENGINE.getCustomCode(HttpStatus.SC_INTERNAL_SERVER_ERROR), capturedCosmos.getReasonErr().getCode());
        assertEquals(PDF_ENGINE_ERROR_MESSAGE, capturedCosmos.getReasonErr().getMessage());
    }

    @Test
    void runKoBlobStorage() throws ReceiptNotFoundException {
        ReceiptCosmosClientImpl cosmosClient = mock(ReceiptCosmosClientImpl.class);
        receiptMock.setStatus(ReceiptStatusType.INSERTED);
        EventData eventDataMock = mock(EventData.class);
        when(eventDataMock.getDebtorFiscalCode()).thenReturn(VALID_DEBTOR_CF);
        when(eventDataMock.getPayerFiscalCode()).thenReturn(VALID_PAYER_CF);
        receiptMock.setEventData(eventDataMock);
        when(cosmosClient.getReceiptDocument(any())).thenReturn(receiptMock);

        GenerateReceiptPdfTest.setMock(ReceiptCosmosClientImpl.class, cosmosClient);

        PdfEngineClientImpl pdfEngineClient = mock(PdfEngineClientImpl.class);
        when(pdfEngineResponse.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        when(pdfEngineResponse.getTempDirectoryPath()).thenReturn(tempDirectoryDebtor.getAbsolutePath());
        when(pdfEngineResponse.getTempPdfPath()).thenReturn(outputPdfDebtor.getAbsolutePath());

        when(pdfEngineClient.generatePDF(any())).thenReturn(pdfEngineResponse);

        GenerateReceiptPdfTest.setMock(PdfEngineClientImpl.class, pdfEngineClient);

        ReceiptBlobClientImpl blobClient = mock(ReceiptBlobClientImpl.class);
        when(blobStorageResponse.getStatusCode()).thenReturn(com.microsoft.azure.functions.HttpStatus.FORBIDDEN.value());
        when(blobClient.savePdfToBlobStorage(any(), anyString())).thenReturn(blobStorageResponse);

        GenerateReceiptPdfTest.setMock(ReceiptBlobClientImpl.class, blobClient);

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        @SuppressWarnings("unchecked")
        OutputBinding<String> requeueMessage = (OutputBinding<String>) spy(OutputBinding.class);

        // test execution
        assertDoesNotThrow(() -> function.processGenerateReceipt(BIZ_EVENT_MESSAGE_DIFFERENT_CF, documentdb, requeueMessage, context));

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt capturedCosmos = receiptCaptor.getValue().get(0);

        verify(requeueMessage).setValue(messageCaptor.capture());
        String caputuredMessage = messageCaptor.getValue();

        assertEquals(BIZ_EVENT_MESSAGE_DIFFERENT_CF, caputuredMessage);
        assertEquals(ReceiptStatusType.RETRY, capturedCosmos.getStatus());
        assertEquals(ReasonErrorCode.ERROR_BLOB_STORAGE.getCode(), capturedCosmos.getReasonErr().getCode());
    }

    @Test
    void runKoBlobStorageThrowException() throws Exception {
        ReceiptCosmosClientImpl cosmosClient = mock(ReceiptCosmosClientImpl.class);
        receiptMock.setStatus(ReceiptStatusType.INSERTED);
        EventData eventDataMock = mock(EventData.class);
        when(eventDataMock.getDebtorFiscalCode()).thenReturn(VALID_DEBTOR_CF);
        when(eventDataMock.getPayerFiscalCode()).thenReturn(VALID_PAYER_CF);
        receiptMock.setEventData(eventDataMock);
        when(cosmosClient.getReceiptDocument(any())).thenReturn(receiptMock);

        GenerateReceiptPdfTest.setMock(ReceiptCosmosClientImpl.class, cosmosClient);

        PdfEngineClientImpl pdfEngineClient = mock(PdfEngineClientImpl.class);
        when(pdfEngineResponse.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        when(pdfEngineResponse.getTempDirectoryPath()).thenReturn(tempDirectoryDebtor.getAbsolutePath());
        when(pdfEngineResponse.getTempPdfPath()).thenReturn(outputPdfDebtor.getAbsolutePath());

        when(pdfEngineClient.generatePDF(any())).thenReturn(pdfEngineResponse);

        GenerateReceiptPdfTest.setMock(PdfEngineClientImpl.class, pdfEngineClient);

        ReceiptBlobClientImpl blobClient = mock(ReceiptBlobClientImpl.class);
        when(blobStorageResponse.getStatusCode()).thenReturn(com.microsoft.azure.functions.HttpStatus.FORBIDDEN.value());
        when(blobClient.savePdfToBlobStorage(any(), anyString())).thenReturn(blobStorageResponse);

        GenerateReceiptPdfTest.setMock(ReceiptBlobClientImpl.class, blobClient);

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        @SuppressWarnings("unchecked")
        OutputBinding<String> requeueMessage = (OutputBinding<String>) spy(OutputBinding.class);

        // test execution
        withEnvironmentVariable("BLOB_STORAGE_CONTAINER_NAME", "wrong-container-name").execute(() ->
                assertDoesNotThrow(() ->
                        function.processGenerateReceipt(BIZ_EVENT_MESSAGE_DIFFERENT_CF, documentdb, requeueMessage, context))
        );

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt capturedCosmos = receiptCaptor.getValue().get(0);

        verify(requeueMessage).setValue(messageCaptor.capture());
        String caputuredMessage = messageCaptor.getValue();

        assertEquals(BIZ_EVENT_MESSAGE_DIFFERENT_CF, caputuredMessage);
        assertEquals(ReceiptStatusType.RETRY, capturedCosmos.getStatus());
        assertEquals(ReasonErrorCode.ERROR_BLOB_STORAGE.getCode(), capturedCosmos.getReasonErr().getCode());
    }

    @Test
    void runKoTooManyRetry() throws ReceiptNotFoundException {
        ReceiptCosmosClientImpl cosmosClient = mock(ReceiptCosmosClientImpl.class);
        receiptMock.setStatus(ReceiptStatusType.RETRY);
        EventData eventDataMock = mock(EventData.class);
        when(eventDataMock.getDebtorFiscalCode()).thenReturn(VALID_DEBTOR_CF);
        when(eventDataMock.getPayerFiscalCode()).thenReturn(VALID_DEBTOR_CF);
        receiptMock.setEventData(eventDataMock);
        receiptMock.setNumRetry(6);
        when(cosmosClient.getReceiptDocument(any())).thenReturn(receiptMock);

        GenerateReceiptPdfTest.setMock(ReceiptCosmosClientImpl.class, cosmosClient);

        PdfEngineClientImpl pdfEngineClient = mock(PdfEngineClientImpl.class);
        when(pdfEngineResponse.getStatusCode()).thenReturn(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        when(pdfEngineClient.generatePDF(any())).thenReturn(pdfEngineResponse);

        GenerateReceiptPdfTest.setMock(PdfEngineClientImpl.class, pdfEngineClient);

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        @SuppressWarnings("unchecked")
        OutputBinding<String> requeueMessage = (OutputBinding<String>) spy(OutputBinding.class);

        // test execution
        assertDoesNotThrow(() -> function.processGenerateReceipt(BIZ_EVENT_MESSAGE_SAME_CF, documentdb, requeueMessage, context));

        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt capturedCosmos = receiptCaptor.getValue().get(0);

        assertEquals(ReceiptStatusType.FAILED, capturedCosmos.getStatus());
    }

    @Test
    void runKoTemplateNotFound() throws Exception {
        ReceiptCosmosClientImpl cosmosClient = mock(ReceiptCosmosClientImpl.class);
        receiptMock.setStatus(ReceiptStatusType.INSERTED);
        EventData eventDataMock = mock(EventData.class);
        when(eventDataMock.getDebtorFiscalCode()).thenReturn(VALID_DEBTOR_CF);
        when(eventDataMock.getPayerFiscalCode()).thenReturn(VALID_DEBTOR_CF);
        receiptMock.setEventData(eventDataMock);
        receiptMock.setNumRetry(0);
        when(cosmosClient.getReceiptDocument(any())).thenReturn(receiptMock);

        GenerateReceiptPdfTest.setMock(ReceiptCosmosClientImpl.class, cosmosClient);

        PdfEngineClientImpl pdfEngineClient = mock(PdfEngineClientImpl.class);

        GenerateReceiptPdfTest.setMock(PdfEngineClientImpl.class, pdfEngineClient);

        @SuppressWarnings("unchecked")
        OutputBinding<List<Receipt>> documentdb = (OutputBinding<List<Receipt>>) spy(OutputBinding.class);

        @SuppressWarnings("unchecked")
        OutputBinding<String> requeueMessage = (OutputBinding<String>) spy(OutputBinding.class);

        // test execution
        withEnvironmentVariable("COMPLETE_TEMPLATE_FILE_NAME", "invalid_filename")
                .execute(() -> {
                    assertDoesNotThrow(() -> function.processGenerateReceipt(BIZ_EVENT_MESSAGE_SAME_CF, documentdb, requeueMessage, context));
                });


        verify(documentdb).setValue(receiptCaptor.capture());
        Receipt capturedCosmos = receiptCaptor.getValue().get(0);

        verify(requeueMessage).setValue(messageCaptor.capture());
        String caputuredMessage = messageCaptor.getValue();

        assertEquals(BIZ_EVENT_MESSAGE_SAME_CF, caputuredMessage);
        assertEquals(ReceiptStatusType.RETRY, capturedCosmos.getStatus());
        assertNotNull(capturedCosmos.getReasonErr().getMessage());
    }

    private static <T> void setMock(Class<T> classToMock, T mock) {
        try {
            Field instance = classToMock.getDeclaredField("instance");
            instance.setAccessible(true);
            instance.set(instance, mock);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
