package it.gov.pagopa.receipt.pdf.datastore.client.impl;

import com.azure.core.http.rest.Response;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlockBlobItem;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptBlobClient;
import it.gov.pagopa.receipt.pdf.datastore.model.response.BlobStorageResponse;

import java.io.InputStream;

/**
 * Client for the Blob Storage
 */
public class ReceiptBlobClientImpl implements ReceiptBlobClient {

    private static ReceiptBlobClientImpl instance;

    private final String containerName = System.getenv("BLOB_STORAGE_CONTAINER_NAME");

    private static final String FILE_EXTENSION = ".pdf";

    private final BlobServiceClient blobServiceClient;

    private ReceiptBlobClientImpl() {
        String connectionString = System.getenv("BLOB_STORAGE_CONN_STRING");
        String storageAccount = System.getenv("BLOB_STORAGE_ACCOUNT_ENDPOINT");

        this.blobServiceClient = new BlobServiceClientBuilder()
                .endpoint(storageAccount)
                .connectionString(connectionString)
                .buildClient();
    }

    public ReceiptBlobClientImpl(BlobServiceClient serviceClient) {
        this.blobServiceClient = serviceClient;
    }

    public static ReceiptBlobClientImpl getInstance() {
        if (instance == null) {
            instance = new ReceiptBlobClientImpl();
        }

        return instance;
    }

    /**
     * Handles saving the PDF to the blob storage
     *
     * @param pdf      PDF file
     * @param fileName Filename to save the PDF with
     * @return blob storage response with PDF metadata or error message and status
     */
    public BlobStorageResponse savePdfToBlobStorage(InputStream pdf, String fileName) {

        //Create the container and return a container client object
        BlobContainerClient blobContainerClient = this.blobServiceClient.getBlobContainerClient(containerName);
        String fileNamePdf = fileName + FILE_EXTENSION;

        //Get a reference to a blob
        BlobClient blobClient = blobContainerClient.getBlobClient(fileNamePdf);

        //Upload the blob
        Response<BlockBlobItem> blockBlobItemResponse = blobClient.uploadWithResponse(
                new BlobParallelUploadOptions(
                        pdf
                ), null, null);

        BlobStorageResponse blobStorageResponse = new BlobStorageResponse();

        //Build response accordingly
        int statusCode = blockBlobItemResponse.getStatusCode();

        if (statusCode == HttpStatus.CREATED.value()) {
            blobStorageResponse.setDocumentName(blobClient.getBlobName());
            blobStorageResponse.setDocumentUrl(blobClient.getBlobUrl());
        }

        blobStorageResponse.setStatusCode(statusCode);

        return blobStorageResponse;
    }
}
