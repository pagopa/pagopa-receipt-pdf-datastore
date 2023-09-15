import { BlobServiceClient } from "@azure/storage-blob";
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);

//ENVIRONMENTAL VARIABLES
const blobStorageConnString = process.env.BLOB_STORAGE_CONN_STRING;
const receiptCosmosDBConnString = process.env.RECEIPT_COSMOS_CONN_STRING;
const bizEventCosmosDBConnString = process.env.BIZEVENT_COSMOS_CONN_STRING;

const environmentString = process.env.ENVIRONMENT_STRING || "local";
let environmentVars = require(`../${environmentString}.environment.json`)?.environment?.[0] || {};

const blobStorageContainerID = environmentVars.blobStorageContainerID;

const bizEventDatabaseID = environmentVars.bizEventDatabaseID;
const bizEventContainerID = environmentVars.bizEventContainerID;

const receiptDatabaseID = environmentVars.receiptDatabaseID;
const receiptContainerID = environmentVars.receiptContainerID;

//CLIENTS
const blobServiceClient = BlobServiceClient.fromConnectionString(blobStorageConnString || "");
export const blobContainerClient = blobServiceClient.getContainerClient(blobStorageContainerID || "");

const receiptClient = new CosmosClient(receiptCosmosDBConnString);
export const receiptContainer = receiptClient.database(receiptDatabaseID).container(receiptContainerID);

const bizeventClient = new CosmosClient(bizEventCosmosDBConnString);
export const bizeventContainer = bizeventClient.database(bizEventDatabaseID).container(bizEventContainerID);