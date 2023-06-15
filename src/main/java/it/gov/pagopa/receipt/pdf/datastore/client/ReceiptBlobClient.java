package it.gov.pagopa.receipt.pdf.datastore.client;

import it.gov.pagopa.receipt.pdf.datastore.model.response.BlobStorageResponse;

public interface ReceiptBlobClient {

    BlobStorageResponse savePdfToBlobStorage(byte[] pdf, String fileName);
}
