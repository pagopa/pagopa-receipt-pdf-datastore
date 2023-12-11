package it.gov.pagopa.receipt.pdf.datastore.client;

import com.azure.cosmos.models.CosmosItemResponse;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;

public interface CartReceiptsCosmosClient {

    CartForReceipt getCartItem(String eventId) throws CartNotFoundException;

    CosmosItemResponse<CartForReceipt> saveCart(CartForReceipt receipt);
}
