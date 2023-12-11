package it.gov.pagopa.receipt.pdf.datastore.entity.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CartForReceipt {

    private String id;
    private List<String> cartPaymentId;

    private Integer totalNotice;

}
