package it.gov.pagopa.receipt.pdf.datastore.entity.cart;

import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReceiptMetadata;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Payload {
    private String payerFiscalCode;
//    private String debtorFiscalCode; moved to CartItem
    private String transactionCreationDate;
    private String totalAmount; // renamed
    private ReceiptMetadata mdAttachPayer;
    private List<CartItem> cart;
}
