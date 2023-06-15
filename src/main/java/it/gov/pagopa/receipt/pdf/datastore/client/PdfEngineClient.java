package it.gov.pagopa.receipt.pdf.datastore.client;

import it.gov.pagopa.receipt.pdf.datastore.model.request.PdfEngineRequest;
import it.gov.pagopa.receipt.pdf.datastore.model.response.PdfEngineResponse;

public interface PdfEngineClient {

    PdfEngineResponse generatePDF(PdfEngineRequest pdfEngineRequest);
}
