package it.gov.pagopa.receipt.pdf.datastore.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PdfEngineErrorMessage {
    private String message;
}
