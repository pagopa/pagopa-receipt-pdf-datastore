package it.gov.pagopa.receipt.pdf.datastore.client;

import com.azure.core.http.rest.Response;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptBlobClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.model.response.BlobStorageResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

class ReceiptBlobClientImplTest {

    @Test
    void testSingleton() throws Exception {
        @SuppressWarnings("secrets:S6338")
        String mockKey = "mockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeymockKeyMK==";
        withEnvironmentVariables(
                "BLOB_STORAGE_CONN_STRING", "DefaultEndpointsProtocol=https;AccountName=samplestorage;AccountKey="+mockKey+";EndpointSuffix=core.windows.net",
                "BLOB_STORAGE_ACCOUNT_ENDPOINT", "https://samplestorage.blob.core.windows.net"
        ).execute(() -> Assertions.assertDoesNotThrow(ReceiptBlobClientImpl::getInstance)
        );
    }

    @Test
    void runOk() throws IOException {
        BlobServiceClient mockServiceClient = mock(BlobServiceClient.class);
        BlobContainerClient mockContainer = mock(BlobContainerClient.class);
        BlobClient mockClient = mock(BlobClient.class);

        Response mockBlockItem = mock(Response.class);

        when(mockBlockItem.getStatusCode()).thenReturn(HttpStatus.CREATED.value());

        when(mockClient.uploadWithResponse(any(), eq(null), eq(null))).thenReturn(
                mockBlockItem
        );
        String VALID_BLOB_NAME = "a valid blob name";
        String VALID_BLOB_URL = "a valid blob url";
        when(mockClient.getBlobName()).thenReturn(VALID_BLOB_NAME);
        when(mockClient.getBlobUrl()).thenReturn(VALID_BLOB_URL);

        when(mockContainer.getBlobClient(any())).thenReturn(mockClient);

        when(mockServiceClient.getBlobContainerClient(any())).thenReturn(mockContainer);

        ReceiptBlobClientImpl receiptBlobClient = new ReceiptBlobClientImpl(mockServiceClient);

        BlobStorageResponse response = receiptBlobClient.savePdfToBlobStorage(InputStream.nullInputStream(), "filename");

        assertEquals(HttpStatus.CREATED.value(), response.getStatusCode());
        assertEquals(VALID_BLOB_NAME, response.getDocumentName());
        assertEquals(VALID_BLOB_URL, response.getDocumentUrl());

    }

    @Test
    void runKo() throws IOException {
        BlobServiceClient mockServiceClient = mock(BlobServiceClient.class);
        BlobContainerClient mockContainer = mock(BlobContainerClient.class);
        BlobClient mockClient = mock(BlobClient.class);

        Response mockBlockItem = mock(Response.class);

        when(mockBlockItem.getStatusCode()).thenReturn(HttpStatus.NO_CONTENT.value());

        when(mockClient.uploadWithResponse(any(), eq(null), eq(null))).thenReturn(
                mockBlockItem
        );
        String VALID_BLOB_NAME = "a valid blob name";
        String VALID_BLOB_URL = "a valid blob url";
        when(mockClient.getBlobName()).thenReturn(VALID_BLOB_NAME);
        when(mockClient.getBlobUrl()).thenReturn(VALID_BLOB_URL);

        when(mockContainer.getBlobClient(any())).thenReturn(mockClient);

        when(mockServiceClient.getBlobContainerClient(any())).thenReturn(mockContainer);

        ReceiptBlobClientImpl receiptBlobClient = new ReceiptBlobClientImpl(mockServiceClient);

        BlobStorageResponse response = receiptBlobClient.savePdfToBlobStorage(InputStream.nullInputStream(), "filename");

        assertEquals(HttpStatus.NO_CONTENT.value(), response.getStatusCode());
        assertNull(response.getDocumentName());
        assertNull(response.getDocumentUrl());

    }

}