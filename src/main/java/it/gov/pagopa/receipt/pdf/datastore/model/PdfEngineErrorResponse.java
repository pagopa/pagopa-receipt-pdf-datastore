package it.gov.pagopa.receipt.pdf.datastore.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.microsoft.azure.functions.HttpStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Model class for HTTP error response
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PdfEngineErrorResponse {

    private List<PdfEngineErrorMessage> errors;

}
