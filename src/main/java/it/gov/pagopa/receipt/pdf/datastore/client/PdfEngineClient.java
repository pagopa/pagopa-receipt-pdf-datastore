package it.gov.pagopa.receipt.pdf.datastore.client;

import feign.Headers;
import feign.RequestLine;
import feign.Response;
import it.gov.pagopa.receipt.pdf.datastore.model.request.PdfEngineRequest;

public interface PdfEngineClient {

    @RequestLine("POST /generate-pdf")
    @Headers("Content-type: multipart/form-data")
    Response generatePDF(PdfEngineRequest request);
}
