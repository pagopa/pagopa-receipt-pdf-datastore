package it.gov.pagopa.receipt.pdf.datastore.model.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Model class for PDF engine request
 */
@Getter
@Setter
@NoArgsConstructor
public class PdfEngineRequest {

    byte[] template;
    String data;
    boolean applySignature;
}
