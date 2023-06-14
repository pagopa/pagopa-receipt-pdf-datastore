package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import it.gov.pagopa.receipt.pdf.datastore.client.PdfEngineClient;
import it.gov.pagopa.receipt.pdf.datastore.model.request.PdfEngineRequest;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.IOException;

public class PdfEngineClientImpl implements PdfEngineClient {

    public void generatePDF(PdfEngineRequest pdfEngineRequest) {

        try (CloseableHttpClient client = HttpClientBuilder.create().build()) {
            FileBody fileBody = new FileBody(pdfEngineRequest.getTemplate(), ContentType.DEFAULT_BINARY);
            StringBody dataBody = new StringBody(pdfEngineRequest.getData(), ContentType.APPLICATION_JSON);

            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            builder.addPart("template", fileBody);
            builder.addPart("data", dataBody);
            HttpEntity entity = builder.build();

            HttpPost request = new HttpPost(System.getenv("PDF_ENGINE_ENDPOINT"));
            request.setEntity(entity);
            client.execute(request);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
