package it.gov.pagopa.receipt.pdf.datastore.model.request;

import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.File;
import java.util.Map;

@Setter
@NoArgsConstructor
public class PdfEngineRequest {

    File template;
    Map<String, Object> data;
    boolean applySignature;
}
