package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import it.gov.pagopa.receipt.pdf.datastore.client.PdfEngineClient;
import it.gov.pagopa.receipt.pdf.datastore.model.PdfEngineErrorResponse;
import it.gov.pagopa.receipt.pdf.datastore.model.request.PdfEngineRequest;
import it.gov.pagopa.receipt.pdf.datastore.model.response.PdfEngineResponse;
import it.gov.pagopa.receipt.pdf.datastore.utils.ObjectMapperUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class PdfEngineClientImpl implements PdfEngineClient {

    private static PdfEngineClientImpl instance = null;

    private final String pdfEngineEndpoint = System.getenv("PDF_ENGINE_ENDPOINT");
    private final String ocpAimSubKey = System.getenv("OCP_APIM_SUBSCRIPTION_KEY");
    private static final String HEADER_AUTH_KEY = "Ocp-Apim-Subscription-Key";
    private static final String ZIP_FILE_NAME = "template.zip";
    private static final String TEMPLATE_KEY = "template";
    private static final String DATA_KEY = "data";

    private PdfEngineClientImpl(){}

    public static PdfEngineClientImpl getInstance() {
        if (instance == null) {
            instance = new PdfEngineClientImpl();
        }

        return instance;
    }

    public PdfEngineResponse generatePDF(PdfEngineRequest pdfEngineRequest) {

        PdfEngineResponse pdfEngineResponse = new PdfEngineResponse();

        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            ByteArrayBody fileBody = new ByteArrayBody(pdfEngineRequest.getTemplate(), ZIP_FILE_NAME);
            StringBody dataBody = new StringBody(pdfEngineRequest.getData(), ContentType.APPLICATION_JSON);

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            builder.addPart(TEMPLATE_KEY, fileBody);
            builder.addPart(DATA_KEY, dataBody);
            HttpEntity entity = builder.build();

            HttpPost request = new HttpPost(pdfEngineEndpoint);
            request.setHeader(HEADER_AUTH_KEY, ocpAimSubKey);
            request.setEntity(entity);

            handlePdfEngineResponse(pdfEngineResponse, client, request);
        } catch (IOException e) {
            pdfEngineResponse.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }

        return pdfEngineResponse;
    }

    private static void handlePdfEngineResponse(PdfEngineResponse pdfEngineResponse, CloseableHttpClient client, HttpPost request) {
        try (CloseableHttpResponse response = client.execute(request)) {
            HttpEntity entityResponse = response.getEntity();

            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK && entityResponse != null) {
                InputStream inputStream = entityResponse.getContent();

                pdfEngineResponse.setStatusCode(HttpStatus.SC_OK);
                pdfEngineResponse.setPdf(inputStream.readAllBytes());
            } else {
                pdfEngineResponse.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);

                handleErrorResponse(pdfEngineResponse, response, entityResponse);
            }

        } catch (IOException e) {
            pdfEngineResponse.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private static void handleErrorResponse(PdfEngineResponse pdfEngineResponse, CloseableHttpResponse response, HttpEntity entityResponse) throws IOException {
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
            pdfEngineResponse.setErrorMessage("Unauthorized call to PDF engine function");

        } else if (entityResponse != null) {
            String jsonString = EntityUtils.toString(entityResponse, StandardCharsets.UTF_8);

            if (!jsonString.isEmpty()) {
                PdfEngineErrorResponse errorResponse = ObjectMapperUtils.mapString(jsonString, PdfEngineErrorResponse.class);

                if (errorResponse != null &&
                        errorResponse.getErrors() != null &&
                        !errorResponse.getErrors().isEmpty() &&
                        errorResponse.getErrors().get(0) != null
                ) {
                    pdfEngineResponse.setErrorMessage(errorResponse.getErrors().get(0).getMessage());
                }
            }
        }

        if (pdfEngineResponse.getErrorMessage() == null) {
            pdfEngineResponse.setErrorMessage("Unknown error in PDF engine function");
        }
    }
}
