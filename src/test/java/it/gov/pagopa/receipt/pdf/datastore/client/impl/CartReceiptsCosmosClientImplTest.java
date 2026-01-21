package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.util.CosmosPagedIterable;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.CartReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Iterator;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

@ExtendWith(MockitoExtension.class)
class CartReceiptsCosmosClientImplTest {

    private static final String CART_ID = "1";

    @Mock
    private CosmosClient cosmosClientMock;

    @Mock
    private CosmosDatabase mockDatabase;
    @Mock
    private CosmosContainer mockContainer;
    @Mock
    private CosmosPagedIterable<CartForReceipt> mockCartIterable;
    @Mock
    private Iterable<FeedResponse<CartForReceipt>> mockCartIterableByPage;
    @Mock
    private CosmosPagedIterable<CartReceiptError> mockCartErrorIterable;
    @Mock
    private Iterator<CartForReceipt> mockIterator;
    @Mock
    private Stream<CartForReceipt> mockCartStream;
    @Mock
    private Stream<CartReceiptError> mockCartErrorStream;

    @InjectMocks
    private CartReceiptsCosmosClientImpl sut;

    @Test
    void testSingletonConnectionError() throws Exception {
        String mockKey = "mockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeyMK==";
        withEnvironmentVariables(
                "COSMOS_RECEIPT_KEY", mockKey,
                "COSMOS_RECEIPT_SERVICE_ENDPOINT", "",
                "COSMOS_RECEIPT_READ_REGION", ""
        ).execute(() -> assertThrows(IllegalArgumentException.class, CartReceiptsCosmosClientImpl::getInstance)
        );
    }

    @Test
    void getCartItemSuccess() {
        CartForReceipt cartForReceipt = new CartForReceipt();
        cartForReceipt.setId(CART_ID);

        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.queryItems(anyString(), any(), eq(CartForReceipt.class)))
                .thenReturn(mockCartIterable);
        when(mockCartIterable.stream()).thenReturn(mockCartStream);
        when(mockCartStream.findFirst()).thenReturn(Optional.of(cartForReceipt));

        CartForReceipt result = assertDoesNotThrow(() -> sut.getCartItem(CART_ID));

        assertEquals(CART_ID, result.getId());
    }

    @Test
    void getCartItemFail() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.queryItems(anyString(), any(), eq(CartForReceipt.class)))
                .thenReturn(mockCartIterable);
        when(mockCartIterable.stream()).thenReturn(mockCartStream);
        when(mockCartStream.findFirst()).thenReturn(Optional.empty());

        assertThrows(CartNotFoundException.class, () -> sut.getCartItem("an invalid receipt id"));
    }

    @Test
    void updateCartSuccess() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);

        assertDoesNotThrow(() -> sut.updateCart(new CartForReceipt()));

        verify(mockContainer).upsertItem(any(), any());
    }

    @Test
    void getCartReceiptErrorSuccess() {
        CartReceiptError receiptError = new CartReceiptError();
        receiptError.setId(CART_ID);

        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.queryItems(anyString(), any(), eq(CartReceiptError.class)))
                .thenReturn(mockCartErrorIterable);
        when(mockCartErrorIterable.stream()).thenReturn(mockCartErrorStream);
        when(mockCartErrorStream.findFirst()).thenReturn(Optional.of(receiptError));

        CartReceiptError result = assertDoesNotThrow(() -> sut.getCartReceiptError(CART_ID));

        assertEquals(CART_ID, result.getId());
    }

    @Test
    void getFailedCartReceiptDocumentsSuccess() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.queryItems(anyString(), any(), eq(CartForReceipt.class)))
                .thenReturn(mockCartIterable);
        when(mockCartIterable.iterableByPage(anyString(), anyInt())).thenReturn(mockCartIterableByPage);

        Iterable<FeedResponse<CartForReceipt>> result =
                assertDoesNotThrow(() -> sut.getFailedCartReceiptDocuments("1", 0));

        assertNotNull(result);
    }

    @Test
    void getInsertedCartReceiptDocumentsSuccess() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.queryItems(anyString(), any(), eq(CartForReceipt.class)))
                .thenReturn(mockCartIterable);
        when(mockCartIterable.iterableByPage(anyString(), anyInt())).thenReturn(mockCartIterableByPage);

        Iterable<FeedResponse<CartForReceipt>> result =
                assertDoesNotThrow(() -> sut.getInsertedCartReceiptDocuments("1", 0));

        assertNotNull(result);
    }

    @Test
    void getIOErrorToNotifyCartReceiptDocumentsSuccess() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.queryItems(anyString(), any(), eq(CartForReceipt.class)))
                .thenReturn(mockCartIterable);
        when(mockCartIterable.iterableByPage(anyString(), anyInt())).thenReturn(mockCartIterableByPage);

        Iterable<FeedResponse<CartForReceipt>> result =
                assertDoesNotThrow(() -> sut.getIOErrorToNotifyCartReceiptDocuments("1", 0));

        assertNotNull(result);
    }

    @Test
    void getGeneratedCartReceiptDocumentsSuccess() {
        when(cosmosClientMock.getDatabase(any())).thenReturn(mockDatabase);
        when(mockDatabase.getContainer(any())).thenReturn(mockContainer);
        when(mockContainer.queryItems(anyString(), any(), eq(CartForReceipt.class)))
                .thenReturn(mockCartIterable);
        when(mockCartIterable.iterableByPage(anyString(), anyInt())).thenReturn(mockCartIterableByPage);

        Iterable<FeedResponse<CartForReceipt>> result =
                assertDoesNotThrow(() -> sut.getGeneratedCartReceiptDocuments("1", 0));

        assertNotNull(result);
    }
}