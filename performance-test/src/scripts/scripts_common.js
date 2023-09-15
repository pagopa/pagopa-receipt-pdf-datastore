import { BlobServiceClient } from "@azure/storage-blob";
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);

//ENVIRONMENTAL VARIABLES
const blobStorageConnString = process.env.BLOB_STORAGE_CONN_STRING;

const environmentString = process.env.ENVIRONMENT_STRING || "local";
let environmentVars = require(`../${environmentString}.environment.json`)?.environment?.[0] || {};

const blobStorageContainerID = environmentVars.blobStorageContainerID;

export const bizEventCosmosDBURI = environmentVars.bizEventCosmosDBURI;
export const bizEventDatabaseID = environmentVars.bizEventDatabaseID;
export const bizEventContainerID = environmentVars.bizEventContainerID;
export const bizEventCosmosDBPrimaryKey = process.env.BIZEVENT_COSMOS_DB_SUBSCRIPTION_KEY;

export const receiptCosmosDBURI = environmentVars.receiptCosmosDBURI;
export const receiptDatabaseID = environmentVars.receiptDatabaseID;
export const receiptContainerID = environmentVars.receiptContainerID;
export const receiptCosmosDBPrimaryKey = process.env.RECEIPT_COSMOS_DB_SUBSCRIPTION_KEY;

//CLIENTS
const blobServiceClient = BlobServiceClient.fromConnectionString(
    blobStorageConnString || ""
);
export const blobContainerClient = blobServiceClient.getContainerClient(
    blobStorageContainerID || ""
);
