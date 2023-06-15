package it.gov.pagopa.receipt.pdf.datastore.model;

import it.gov.pagopa.receipt.pdf.datastore.model.response.BlobStorageResponse;
import it.gov.pagopa.receipt.pdf.datastore.model.response.PdfEngineResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PdfGeneration {

    int statusCode;
    String errorMessage;
    String documentName;
    String documentUrl;
    byte[] mdAttach;


}
