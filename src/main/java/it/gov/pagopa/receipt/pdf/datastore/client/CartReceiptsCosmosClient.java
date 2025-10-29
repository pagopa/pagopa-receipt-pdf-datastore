package it.gov.pagopa.receipt.pdf.datastore.client;

import com.azure.cosmos.models.CosmosItemResponse;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartConcurrentUpdateException;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;

public interface CartReceiptsCosmosClient {

    CartForReceipt getCartItem(String eventId) throws CartNotFoundException;

    CosmosItemResponse<CartForReceipt> saveCart(CartForReceipt receipt);

    CosmosItemResponse<CartForReceipt> updateCart(CartForReceipt receipt) throws CartConcurrentUpdateException;
}
