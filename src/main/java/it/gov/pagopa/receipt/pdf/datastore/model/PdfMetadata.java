package it.gov.pagopa.receipt.pdf.datastore.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PdfMetadata {

    int statusCode;
    String errorMessage;
    String documentName;
    String documentUrl;
}
