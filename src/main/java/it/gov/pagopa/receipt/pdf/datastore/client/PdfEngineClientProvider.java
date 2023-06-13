package it.gov.pagopa.receipt.pdf.datastore.client;

import it.gov.pagopa.receipt.pdf.datastore.client.PdfEngineClient;

public interface PdfEngineClientProvider {

    PdfEngineClient provideClient();
}
