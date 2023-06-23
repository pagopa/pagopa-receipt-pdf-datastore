package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
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

/**
 * Client for the PDF Engine
 */
public class PdfEngineClientImpl implements PdfEngineClient {

    private static PdfEngineClientImpl instance = null;

    private final String pdfEngineEndpoint = System.getenv().getOrDefault("PDF_ENGINE_ENDPOINT", "");
    private final String ocpAimSubKey = System.getenv().getOrDefault("OCP_APIM_SUBSCRIPTION_KEY", "");
    private static final String HEADER_AUTH_KEY = "Ocp-Apim-Subscription-Key";
    private static final String ZIP_FILE_NAME = "template.zip";
    private static final String TEMPLATE_KEY = "template";
    private static final String DATA_KEY = "data";

    private final HttpClientBuilder httpClientBuilder;

    private PdfEngineClientImpl() {
        this.httpClientBuilder = HttpClientBuilder.create();
    }

    public PdfEngineClientImpl(HttpClientBuilder clientBuilder) {
        this.httpClientBuilder = clientBuilder;
    }

    public static PdfEngineClientImpl getInstance() {
        if (instance == null) {
            instance = new PdfEngineClientImpl();
        }

        return instance;
    }

    /**
     * Generate the client, builds the request and returns the response
     *
     * @param pdfEngineRequest Request to the client
     * @return response with the PDF or error message and the status
     */
    public PdfEngineResponse generatePDF(PdfEngineRequest pdfEngineRequest) {

        PdfEngineResponse pdfEngineResponse = new PdfEngineResponse();

        //Generate client
        try (CloseableHttpClient client = this.httpClientBuilder.build()) {
            //Encode template and data
            ByteArrayBody fileBody = new ByteArrayBody(pdfEngineRequest.getTemplate(), ZIP_FILE_NAME);
            StringBody dataBody = new StringBody(pdfEngineRequest.getData(), ContentType.APPLICATION_JSON);

            //Build the multipart request
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            builder.addPart(TEMPLATE_KEY, fileBody);
            builder.addPart(DATA_KEY, dataBody);
            HttpEntity entity = builder.build();

            //Set endpoint and auth key
            HttpPost request = new HttpPost(pdfEngineEndpoint);
            request.setHeader(HEADER_AUTH_KEY, ocpAimSubKey);
            request.setEntity(entity);

            handlePdfEngineResponse(pdfEngineResponse, client, request);
        } catch (IOException e) {
            pdfEngineResponse.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }

        return pdfEngineResponse;
    }

    /**
     * Calls the PDF Engine and handles its response, updating the PdfEngineResponse accordingly
     *
     * @param pdfEngineResponse Response output
     * @param client            The previously generated client
     * @param request           The request to the PDF engine
     */
    private static void handlePdfEngineResponse(PdfEngineResponse pdfEngineResponse, CloseableHttpClient client, HttpPost request) {
        //Execute call
        try (CloseableHttpResponse response = client.execute(request)) {
            //Retrieve response
            HttpEntity entityResponse = response.getEntity();

            //Handles response
            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK && entityResponse != null) {
                try (InputStream inputStream = entityResponse.getContent()) {
                    pdfEngineResponse.setStatusCode(HttpStatus.SC_OK);
                    pdfEngineResponse.setPdf(inputStream.readAllBytes());
                }
            } else {
                pdfEngineResponse.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);

                handleErrorResponse(pdfEngineResponse, response, entityResponse);
            }

        } catch (IOException e) {
            pdfEngineResponse.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Handles error response from the PDF Engine
     *
     * @param pdfEngineResponse Response to update
     * @param response          Response from the PDF engine
     * @param entityResponse    Response content from the PDF Engine
     * @throws IOException in case of error encoding to string
     */
    private static void handleErrorResponse(PdfEngineResponse pdfEngineResponse, CloseableHttpResponse response, HttpEntity entityResponse) throws IOException {
        //Verify if unauthorized
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
            pdfEngineResponse.setErrorMessage("Unauthorized call to PDF engine function");

        } else if (entityResponse != null) {
            //Handle JSON response
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
