package it.gov.pagopa.receipt.pdf.datastore.client;

import it.gov.pagopa.receipt.pdf.datastore.model.request.PdfEngineRequest;

public interface PdfEngineClient {

    void generatePDF(PdfEngineRequest pdfEngineRequest);
}
