package it.gov.pagopa.receipt.pdf.datastore.model.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.InputStream;

/**
 * Model class for PDF engine request
 */
@Getter
@Setter
@NoArgsConstructor
public class PdfEngineRequest {

    InputStream template;
    String data;
    boolean applySignature;
}
