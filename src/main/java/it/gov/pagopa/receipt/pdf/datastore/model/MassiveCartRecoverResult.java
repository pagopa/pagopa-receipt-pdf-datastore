package it.gov.pagopa.receipt.pdf.datastore.model;

import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MassiveCartRecoverResult {

    private List<CartForReceipt> failedCartList;
    private int errorCounter;
    private int successCounter;
}
