package it.gov.pagopa.receipt.pdf.datastore.client;

import feign.Headers;
import feign.RequestLine;
import it.gov.pagopa.receipt.pdf.datastore.model.request.PdfEngineRequest;
import it.gov.pagopa.receipt.pdf.datastore.model.response.PdfEngineResponse;

public interface PdfEngineClient {

    @RequestLine("POST /generate-pdf")
    @Headers("Content-type: application/json")
    PdfEngineResponse generatePDF(PdfEngineRequest request);
}
