package it.gov.pagopa.receipt.pdf.datastore.model.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PdfEngineResponse {

    byte[] pdf;
    int statusCode;
    String errorMessage;

}
