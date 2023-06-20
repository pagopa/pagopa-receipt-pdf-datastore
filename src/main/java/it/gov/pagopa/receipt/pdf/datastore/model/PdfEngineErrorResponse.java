package it.gov.pagopa.receipt.pdf.datastore.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Model class for PDF engine HTTP error response
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PdfEngineErrorResponse {

    private List<PdfEngineErrorMessage> errors;

}
