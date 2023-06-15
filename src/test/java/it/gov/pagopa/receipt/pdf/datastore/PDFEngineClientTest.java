package it.gov.pagopa.receipt.pdf.datastore;

import it.gov.pagopa.receipt.pdf.datastore.client.impl.PdfEngineClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.model.request.PdfEngineRequest;
import it.gov.pagopa.receipt.pdf.datastore.model.response.PdfEngineResponse;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.*;

public class PDFEngineClientTest {

    @Test
    public void runOk() throws IOException {

        File template = new File("src/main/resources/template.zip");

        try (FileInputStream fileInputStream = new FileInputStream(new File("src/test/resources/receipt_data.json"))) {
            String data = new String(fileInputStream.readAllBytes());

            PdfEngineClientImpl client = new PdfEngineClientImpl();

            PdfEngineRequest pdfEngineRequest = new PdfEngineRequest();
            pdfEngineRequest.setTemplate(template);
            pdfEngineRequest.setData(data);

            PdfEngineResponse pdfEngineResponse = client.generatePDF(pdfEngineRequest);

            Assertions.assertEquals(HttpStatus.SC_OK, pdfEngineResponse.getStatusCode());
            Assertions.assertNotNull(pdfEngineResponse.getPdf());
        }

    }
}
