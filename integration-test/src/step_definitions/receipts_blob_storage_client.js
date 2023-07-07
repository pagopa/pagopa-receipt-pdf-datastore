const { BlobServiceClient } = require("@azure/storage-blob");

const blob_storage_conn_string  = process.env.RECEIPTS_STORAGE_CONN_STRING || "";
const containerName             = process.env.BLOB_STORAGE_CONTAINER_NAME;

const blobServiceClient = BlobServiceClient.fromConnectionString(blob_storage_conn_string);
const containerClient = blobServiceClient.getContainerClient(containerName);

async function receiptPDFExist(blobName) {
    let blobs = containerClient.listBlobsFlat();
    for await (const blob of blobs) {
        if (blob.name === blobName) {
            return true;
        }
    }
    return false;
}

module.exports = {
    receiptPDFExist
}