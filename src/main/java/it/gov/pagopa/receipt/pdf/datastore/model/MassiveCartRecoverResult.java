package it.gov.pagopa.receipt.pdf.datastore.model;

import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MassiveCartRecoverResult {

    private List<CartForReceipt> failedCartList;
    private int errorCounter;
    private int successCounter;
}
