package it.gov.pagopa.receipt.pdf.datastore.entity.receipt;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {
    private String subject;
    private String payeeName;
}
