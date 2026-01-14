package it.gov.pagopa.receipt.pdf.datastore.service.impl;

import it.gov.pagopa.receipt.pdf.datastore.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

class CartReceiptCosmosServiceImplTest {

    @Mock
    private ReceiptCosmosClientImpl receiptCosmosClient;
    @Mock
    private CartReceiptsCosmosClient cartReceiptsCosmosClient;

    @InjectMocks
    private CartReceiptCosmosServiceImpl sut;

    @Test
    void getCart_OK() throws CartNotFoundException {
        doReturn(new CartForReceipt()).when(cartReceiptsCosmosClient).getCartItem(anyString());

        assertDoesNotThrow(() -> sut.getCart(anyString()));
    }

    @Test
    void getCart_KO_NotFound() throws CartNotFoundException {
        doThrow(CartNotFoundException.class).when(cartReceiptsCosmosClient).getCartItem(anyString());
        CartNotFoundException e = assertThrows(CartNotFoundException.class, () -> sut.getCart(anyString()));

        assertNotNull(e);
    }

    @Test
    void getCart_KO_Null() throws CartNotFoundException {
        doReturn(null).when(cartReceiptsCosmosClient).getCartItem(anyString());
        CartNotFoundException e = assertThrows(CartNotFoundException.class, () -> sut.getCart(anyString()));

        assertNotNull(e);
    }

}