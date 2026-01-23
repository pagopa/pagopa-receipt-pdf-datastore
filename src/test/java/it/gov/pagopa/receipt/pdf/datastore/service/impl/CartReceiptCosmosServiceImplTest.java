package it.gov.pagopa.receipt.pdf.datastore.service.impl;

import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.datastore.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.CartReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CartReceiptCosmosServiceImplTest {

    @Mock
    private CartReceiptsCosmosClient cartReceiptsCosmosClient;
    @Mock
    private Iterable<FeedResponse<CartForReceipt>> iterableMock;

    @InjectMocks
    private CartReceiptCosmosServiceImpl sut;

    @Test
    void getCartReceiptError_OK() throws CartNotFoundException {
        doReturn(new CartReceiptError()).when(cartReceiptsCosmosClient).getCartReceiptError(anyString());

        assertDoesNotThrow(() -> sut.getCartReceiptError(anyString()));
    }

    @Test
    void getCartReceiptError_KO_NotFound() throws CartNotFoundException {
        doThrow(CartNotFoundException.class).when(cartReceiptsCosmosClient).getCartReceiptError(anyString());
        CartNotFoundException e = assertThrows(CartNotFoundException.class, () -> sut.getCartReceiptError(anyString()));

        assertNotNull(e);
    }

    @Test
    void getCartReceiptError_KO_Null() throws CartNotFoundException {
        doReturn(null).when(cartReceiptsCosmosClient).getCartReceiptError(anyString());
        CartNotFoundException e = assertThrows(CartNotFoundException.class, () -> sut.getCartReceiptError(anyString()));

        assertNotNull(e);
    }

    @Test
    void getNotNotifiedCartReceiptByStatus_OK_IO_ERROR_TO_NOTIFY() {
        doReturn(iterableMock).when(cartReceiptsCosmosClient).getIOErrorToNotifyCartReceiptDocuments(anyString(), any());

        assertDoesNotThrow(() -> sut.getNotNotifiedCartReceiptByStatus("", 1, CartStatusType.IO_ERROR_TO_NOTIFY));

        verify(cartReceiptsCosmosClient, never()).getGeneratedCartReceiptDocuments(anyString(), any());
    }

    @Test
    void getNotNotifiedCartReceiptByStatus_OK_GENERATED() {
        doReturn(iterableMock).when(cartReceiptsCosmosClient).getGeneratedCartReceiptDocuments(anyString(), any());

        assertDoesNotThrow(() -> sut.getNotNotifiedCartReceiptByStatus("", 1, CartStatusType.GENERATED));

        verify(cartReceiptsCosmosClient, never()).getIOErrorToNotifyCartReceiptDocuments(anyString(), any());
    }

    @Test
    void getNotNotifiedCartReceiptByStatus_KO_StatusNull() {
        assertThrows(
                IllegalArgumentException.class,
                () -> sut.getNotNotifiedCartReceiptByStatus("", 1, null)
        );

        verify(cartReceiptsCosmosClient, never()).getGeneratedCartReceiptDocuments(anyString(), any());
        verify(cartReceiptsCosmosClient, never()).getIOErrorToNotifyCartReceiptDocuments(anyString(), any());
    }

    @ParameterizedTest
    @EnumSource(value = CartStatusType.class, names = {"GENERATED", "IO_ERROR_TO_NOTIFY"}, mode = EnumSource.Mode.EXCLUDE)
    void getNotNotifiedCartReceiptByStatus_KO_InvalidStatus(CartStatusType status) {
        assertThrows(
                IllegalStateException.class,
                () -> sut.getNotNotifiedCartReceiptByStatus("", 1, status)
        );

        verify(cartReceiptsCosmosClient, never()).getGeneratedCartReceiptDocuments(anyString(), any());
        verify(cartReceiptsCosmosClient, never()).getIOErrorToNotifyCartReceiptDocuments(anyString(), any());
    }

    @ParameterizedTest
    @EnumSource(value = CartStatusType.class, names = {"FAILED", "NOT_QUEUE_SENT"})
    void getFailedCartReceiptByStatus_OK_FAILED(CartStatusType status) {
        doReturn(iterableMock).when(cartReceiptsCosmosClient).getFailedCartReceiptDocuments(anyString(), any());

        assertDoesNotThrow(() -> sut.getFailedCartReceiptByStatus("", 1, status));

        verify(cartReceiptsCosmosClient, never()).getInsertedCartReceiptDocuments(anyString(), any());
    }

    @ParameterizedTest
    @EnumSource(value = CartStatusType.class, names = {"INSERTED", "WAITING_FOR_BIZ_EVENT"})
    void getFailedCartReceiptByStatus_OK_INSERTED(CartStatusType status) {
        doReturn(iterableMock).when(cartReceiptsCosmosClient).getInsertedCartReceiptDocuments(anyString(), any());

        assertDoesNotThrow(() -> sut.getFailedCartReceiptByStatus("", 1, status));

        verify(cartReceiptsCosmosClient, never()).getFailedCartReceiptDocuments(anyString(), any());
    }

    @Test
    void getFailedCartReceiptByStatus_KO_StatusNull() {
        assertThrows(
                IllegalArgumentException.class,
                () -> sut.getFailedCartReceiptByStatus("", 1, null)
        );

        verify(cartReceiptsCosmosClient, never()).getFailedCartReceiptDocuments(anyString(), any());
        verify(cartReceiptsCosmosClient, never()).getInsertedCartReceiptDocuments(anyString(), any());
    }

    @ParameterizedTest
    @EnumSource(value = CartStatusType.class, names = {"INSERTED", "WAITING_FOR_BIZ_EVENT","FAILED", "NOT_QUEUE_SENT"}, mode = EnumSource.Mode.EXCLUDE)
    void getFailedCartReceiptByStatus_KO_InvalidStatus(CartStatusType status) {
        assertThrows(
                IllegalStateException.class,
                () -> sut.getFailedCartReceiptByStatus("", 1, status)
        );

        verify(cartReceiptsCosmosClient, never()).getFailedCartReceiptDocuments(anyString(), any());
        verify(cartReceiptsCosmosClient, never()).getInsertedCartReceiptDocuments(anyString(), any());
    }

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