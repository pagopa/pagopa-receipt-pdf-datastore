package it.gov.pagopa.receipt.pdf.datastore.model;

import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MassiveRecoverResult {

    private List<Receipt> failedReceiptList;
    private int errorCounter;
    private int successCounter;
}
