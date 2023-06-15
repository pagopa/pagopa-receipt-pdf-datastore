package it.gov.pagopa.receipt.pdf.datastore.model.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.File;
import java.io.InputStream;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class PdfEngineRequest {

    byte[] template;
    String data;
    boolean applySignature;
}
