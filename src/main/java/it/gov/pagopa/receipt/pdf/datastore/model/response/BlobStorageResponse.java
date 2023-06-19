package it.gov.pagopa.receipt.pdf.datastore.model.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BlobStorageResponse {

    String documentUrl;
    String documentName;
    int statusCode;
}
