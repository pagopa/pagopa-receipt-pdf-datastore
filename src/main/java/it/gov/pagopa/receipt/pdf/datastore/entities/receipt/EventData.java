package it.gov.pagopa.receipt.pdf.datastore.entities.receipt;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EventData {
    private String payerFiscalCode;
    private String debtorFiscalCode;
    private String transactionCreationDate;
}
