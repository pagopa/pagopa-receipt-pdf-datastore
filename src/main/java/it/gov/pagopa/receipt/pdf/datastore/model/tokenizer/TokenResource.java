package it.gov.pagopa.receipt.pdf.datastore.model.tokenizer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model class that hold the token related to a PII
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResource {

    private String token;
}
