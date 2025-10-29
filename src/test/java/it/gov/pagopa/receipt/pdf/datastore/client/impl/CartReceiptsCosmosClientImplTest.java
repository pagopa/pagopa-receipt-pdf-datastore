package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

class CartReceiptsCosmosClientImplTest {

    @Test
    void testSingletonConnectionError() throws Exception {
        String mockKey = "mockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeyMK==";
        withEnvironmentVariables(
                "COSMOS_RECEIPT_KEY", mockKey,
                "COSMOS_RECEIPT_SERVICE_ENDPOINT", ""
        ).execute(() -> Assertions.assertThrows(IllegalArgumentException.class, CartReceiptsCosmosClientImpl::getInstance)
        );
    }

    @Test
    void runOk_Cart() throws CartNotFoundException {
        String CART_ID = "1";

        CosmosClient mockClient = mock(CosmosClient.class);

        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);

        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);

        Iterator<CartForReceipt> mockIterator = mock(Iterator.class);
        CartForReceipt cartForReceipt = new CartForReceipt();
        cartForReceipt.setId(CART_ID);

        when(mockIterator.hasNext()).thenReturn(true);
        when(mockIterator.next()).thenReturn(cartForReceipt);

        when(mockIterable.iterator()).thenReturn(mockIterator);

        when(mockContainer.queryItems(anyString(), any(), eq(CartForReceipt.class))).thenReturn(
                mockIterable
        );
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);

        CartReceiptsCosmosClientImpl client = new CartReceiptsCosmosClientImpl(mockClient);

        Assertions.assertDoesNotThrow(() -> client.getCartItem(CART_ID));

        CartForReceipt cartResponse = client.getCartItem(CART_ID);
        Assertions.assertEquals(CART_ID, cartResponse.getId());
    }

    @Test
    void runKo_Cart() {
        CosmosClient mockClient = mock(CosmosClient.class);

        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);

        CosmosPagedIterable mockIterable = mock(CosmosPagedIterable.class);

        Iterator<Receipt> mockIterator = mock(Iterator.class);

        when(mockIterator.hasNext()).thenReturn(false);

        when(mockIterable.iterator()).thenReturn(mockIterator);

        when(mockContainer.queryItems(anyString(), any(), eq(CartForReceipt.class))).thenReturn(
                mockIterable
        );
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);

        CartReceiptsCosmosClientImpl client = new CartReceiptsCosmosClientImpl(mockClient);

        Assertions.assertThrows(CartNotFoundException.class, () -> client.getCartItem("an invalid receipt id"));
    }

    @Test
    void runOk_SaveCart() throws CartNotFoundException {
        String CART_ID = "1";

        CosmosClient mockClient = mock(CosmosClient.class);

        CosmosDatabase mockDatabase = mock(CosmosDatabase.class);
        CosmosContainer mockContainer = mock(CosmosContainer.class);

        CartForReceipt cartForReceipt = new CartForReceipt();
        cartForReceipt.setId(CART_ID);

        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockClient.getDatabase(any())).thenReturn(mockDatabase);

        CartReceiptsCosmosClientImpl client = new CartReceiptsCosmosClientImpl(mockClient);

        Assertions.assertDoesNotThrow(() -> client.saveCart(cartForReceipt));

    }

}