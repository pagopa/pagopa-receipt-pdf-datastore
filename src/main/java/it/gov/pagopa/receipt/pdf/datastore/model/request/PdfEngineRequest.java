package it.gov.pagopa.receipt.pdf.datastore.model.request;

import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@NoArgsConstructor
public class PdfEngineRequest {

    String template;
    String data;
    boolean applySignature;
}
