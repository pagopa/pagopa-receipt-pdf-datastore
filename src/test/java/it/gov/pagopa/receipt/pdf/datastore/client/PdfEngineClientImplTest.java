package it.gov.pagopa.receipt.pdf.datastore.client;

import it.gov.pagopa.receipt.pdf.datastore.client.impl.PdfEngineClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.model.request.PdfEngineRequest;
import it.gov.pagopa.receipt.pdf.datastore.model.response.PdfEngineResponse;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PdfEngineClientImplTest {

    @Test
    void testSingleton() {
        Assertions.assertDoesNotThrow(PdfEngineClientImpl::getInstance);
    }

    @Test
    void runOk() throws IOException {

        File tempDirectory = new File("temp");
        if (!tempDirectory.exists()) {
            Files.createDirectory(tempDirectory.toPath());
        }

        File targetFile = File.createTempFile("tempFile", ".txt", tempDirectory);


        byte[] template;
        PdfEngineRequest pdfEngineRequest = new PdfEngineRequest();
        try (InputStream inputStream = FileInputStream.nullInputStream()) {
            template = inputStream.readAllBytes();

            pdfEngineRequest.setTemplate(targetFile.toURI().toURL());
            pdfEngineRequest.setData(new String(template));
        } finally {
            targetFile.deleteOnExit();
            tempDirectory.deleteOnExit();
        }

        HttpClientBuilder mockBuilder = mock(HttpClientBuilder.class);
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);

        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        StatusLine mockStatusLine = mock(StatusLine.class);
        when(mockStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);

        HttpEntity mockEntity = mock(HttpEntity.class);
        when(mockEntity.getContent()).thenReturn(InputStream.nullInputStream());
        when(mockResponse.getEntity()).thenReturn(mockEntity);

        when(mockClient.execute(any())).thenReturn(mockResponse);
        when(mockBuilder.build()).thenReturn(mockClient);

        PdfEngineClientImpl client = new PdfEngineClientImpl(mockBuilder);
        PdfEngineResponse pdfEngineResponse = client.generatePDF(pdfEngineRequest);

        File tempPdf = new File(pdfEngineResponse.getTempPdfPath());
        Assertions.assertTrue(tempPdf.delete());
        Assertions.assertEquals(HttpStatus.SC_OK, pdfEngineResponse.getStatusCode());
    }

    @Test
    void runKoUnauthorized() throws IOException {

        File tempDirectory = new File("temp");
        if (!tempDirectory.exists()) {
            Files.createDirectory(tempDirectory.toPath());
        }

        File targetFile = File.createTempFile("tempFile", ".txt", tempDirectory);

        byte[] template;
        PdfEngineRequest pdfEngineRequest = new PdfEngineRequest();
        try (InputStream inputStream = FileInputStream.nullInputStream()) {
            template = inputStream.readAllBytes();

            pdfEngineRequest.setTemplate(targetFile.toURI().toURL());
            pdfEngineRequest.setData(new String(template));
        } finally {
            targetFile.deleteOnExit();
            tempDirectory.deleteOnExit();
        }

        HttpClientBuilder mockBuilder = mock(HttpClientBuilder.class);
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);

        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        StatusLine mockStatusLine = mock(StatusLine.class);
        when(mockStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_UNAUTHORIZED);
        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);

        HttpEntity mockEntity = mock(HttpEntity.class);
        when(mockEntity.getContent()).thenReturn(InputStream.nullInputStream());
        when(mockResponse.getEntity()).thenReturn(mockEntity);

        when(mockClient.execute(any())).thenReturn(mockResponse);
        when(mockBuilder.build()).thenReturn(mockClient);

        PdfEngineClientImpl client = new PdfEngineClientImpl(mockBuilder);
        PdfEngineResponse pdfEngineResponse = client.generatePDF(pdfEngineRequest);

        Assertions.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, pdfEngineResponse.getStatusCode());
        Assertions.assertNotNull(pdfEngineResponse.getErrorMessage());

    }

    @Test
    void runKo400() throws IOException {
        File tempDirectory = new File("temp");
        if (!tempDirectory.exists()) {
            Files.createDirectory(tempDirectory.toPath());
        }

        File targetFile = File.createTempFile("tempFile", ".txt", tempDirectory);
        byte[] template;
        PdfEngineRequest pdfEngineRequest = new PdfEngineRequest();
        try (InputStream inputStream = FileInputStream.nullInputStream()) {
            template = inputStream.readAllBytes();
            pdfEngineRequest.setTemplate(targetFile.toURI().toURL());
            pdfEngineRequest.setData(new String(template));
        } finally {
            targetFile.deleteOnExit();
            tempDirectory.deleteOnExit();
        }

        HttpClientBuilder mockBuilder = mock(HttpClientBuilder.class);
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);

        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        StatusLine mockStatusLine = mock(StatusLine.class);
        when(mockStatusLine.getStatusCode()).thenReturn(HttpStatus.SC_BAD_REQUEST);
        when(mockResponse.getStatusLine()).thenReturn(mockStatusLine);

        HttpEntity mockEntity = mock(HttpEntity.class);
        String ERROR_MESSAGE = "\"Invalid request\"";
        String ERROR_400 = "{\n" +
                "  \"errorId\": \"a3779a25-9c8a-4a6f-9272-a052119cfd2e\",\n" +
                "  \"httpStatusCode\": \"BAD_REQUEST\",\n" +
                "  \"httpStatusDescription\": \"Bad Request\",\n" +
                "  \"appErrorCode\": \"PDFE_898\",\n" +
                "  \"errors\": [\n" +
                "    {\n" +
                "      \"message\": " + ERROR_MESSAGE +
                "    }\n" +
                "  ]\n" +
                "}";
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream(ERROR_400.getBytes()));
        when(mockResponse.getEntity()).thenReturn(mockEntity);

        when(mockClient.execute(any())).thenReturn(mockResponse);
        when(mockBuilder.build()).thenReturn(mockClient);

        PdfEngineClientImpl client = new PdfEngineClientImpl(mockBuilder);
        PdfEngineResponse pdfEngineResponse = client.generatePDF(pdfEngineRequest);

        Assertions.assertEquals(HttpStatus.SC_INTERNAL_SERVER_ERROR, pdfEngineResponse.getStatusCode());
        Assertions.assertEquals(ERROR_MESSAGE.replace("\"", ""), pdfEngineResponse.getErrorMessage());

    }
}
