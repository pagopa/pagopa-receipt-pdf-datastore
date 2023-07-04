package it.gov.pagopa.receipt.pdf.datastore.entity.receipt;

import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptErrorStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceiptError {

    private String id;
    private String messagePayload;
    private String messageError;
    private ReceiptErrorStatusType status;

}
