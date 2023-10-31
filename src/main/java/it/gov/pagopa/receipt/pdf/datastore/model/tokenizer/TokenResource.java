package it.gov.pagopa.receipt.pdf.datastore.model.tokenizer;

import lombok.Builder;
import lombok.Data;

/**
 * Model class that hold the token related to a PII
 */
@Data
@Builder
public class TokenResource {

    private String token;
}
