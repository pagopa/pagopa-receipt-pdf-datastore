package it.gov.pagopa.receipt.pdf.datastore.client;

import it.gov.pagopa.receipt.pdf.datastore.model.response.BlobStorageResponse;

import java.io.InputStream;

public interface ReceiptBlobClient {

    BlobStorageResponse savePdfToBlobStorage(InputStream pdf, String fileName);
}
