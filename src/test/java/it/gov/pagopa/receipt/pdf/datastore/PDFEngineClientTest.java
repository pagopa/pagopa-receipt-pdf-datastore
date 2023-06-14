package it.gov.pagopa.receipt.pdf.datastore;

import it.gov.pagopa.receipt.pdf.datastore.client.impl.PdfEngineClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.model.request.PdfEngineRequest;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class PDFEngineClientTest {

    @Test
    public void runOk() throws IOException {

        File template = new File("src/main/resources/complete_template.zip");

        try(FileInputStream fileInputStream = new FileInputStream(new File("src/test/resources/receipt_data.json"))){
            String data = new String(fileInputStream.readAllBytes());

            PdfEngineClientImpl client = new PdfEngineClientImpl();

            PdfEngineRequest pdfEngineRequest = new PdfEngineRequest();
            pdfEngineRequest.setTemplate(template);
            pdfEngineRequest.setData(data);

            client.generatePDF(pdfEngineRequest);
        }

    }
}
