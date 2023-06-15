package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.core.http.rest.Response;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptBlobClient;
import it.gov.pagopa.receipt.pdf.datastore.model.response.BlobStorageResponse;

import java.io.ByteArrayInputStream;

public class ReceiptBlobClientImpl implements ReceiptBlobClient {

    private final String storageAccount = System.getenv("BLOB_STORAGE_ACCOUNT_ENDPOINT");
    private final String connectionString = System.getenv("BLOB_STORAGE_CONN_STRING");

    private final String containerName = System.getenv("BLOB_STORAGE_CONTAINER_NAME");

    public BlobStorageResponse savePdfToBlobStorage(byte[] pdf, String fileName) {
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .endpoint(storageAccount)
                .connectionString(connectionString)
                .buildClient();

        // Create the container and return a container client object
        BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(containerName);
        String fileNamePdf = fileName+".pdf";

        // Get a reference to a blob
        BlobClient blobClient = blobContainerClient.getBlobClient(fileNamePdf);

        // Upload the blob
        Response<BlockBlobItem> blockBlobItemResponse = blobClient.uploadWithResponse(
                new BlobParallelUploadOptions(
                        new ByteArrayInputStream(pdf)
                ), null, null);

        BlobStorageResponse blobStorageResponse = new BlobStorageResponse();

        int statusCode = blockBlobItemResponse.getStatusCode();

        if(statusCode == 201){
            blobStorageResponse.setDocumentName(blobClient.getBlobName());
            blobStorageResponse.setDocumentUrl(blobClient.getBlobUrl());
            blobStorageResponse.setMdAttach(blockBlobItemResponse.getValue().getContentMd5());
        }

        blobStorageResponse.setStatusCode(blockBlobItemResponse.getStatusCode());

        return blobStorageResponse;
    }
}
