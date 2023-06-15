package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import it.gov.pagopa.receipt.pdf.datastore.client.PdfEngineClient;
import it.gov.pagopa.receipt.pdf.datastore.model.request.PdfEngineRequest;
import it.gov.pagopa.receipt.pdf.datastore.model.response.PdfEngineResponse;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import java.io.IOException;
import java.io.InputStream;

public class PdfEngineClientImpl implements PdfEngineClient {

    private final String pdfEngineEndpoint = System.getenv("PDF_ENGINE_ENDPOINT");
    private final String ocpAimSubKey = System.getenv("OCP_APIM_SUBSCRIPTION_KEY");
    private final String HEADER_AUTH_KEY = "Ocp-Apim-Subscription-Key";
    private final String ZIP_FILE_NAME = "template.zip";
    private final String TEMPLATE_KEY = "template";
    private final String DATA_KEY = "data";

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

            try (CloseableHttpResponse response = client.execute(request)) {
                HttpEntity entityResponse = response.getEntity();

                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK && entityResponse != null) {
                    InputStream inputStream = entityResponse.getContent();

                    pdfEngineResponse.setStatusCode(HttpStatus.SC_OK);
                    pdfEngineResponse.setPdf(inputStream.readAllBytes());
                } else {
                    pdfEngineResponse.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                    //TODO retrieve error message from response json
                    pdfEngineResponse.setErrorMessage(response.getStatusLine().getReasonPhrase());
                }

            } catch (IOException e) {
                pdfEngineResponse.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (IOException e) {
            pdfEngineResponse.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }

        return pdfEngineResponse;
    }
}
