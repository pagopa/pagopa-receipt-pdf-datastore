const { CosmosClient } = require("@azure/cosmos");
const { BlobServiceClient } = require('@azure/storage-blob');
const { TOKENIZED_FISCAL_CODE } = require("../../src/step_definitions/common");

//COSMOS RECEIPT
const cosmos_db_conn_string = process.env.RECEIPTS_COSMOS_CONN_STRING || "";
const databaseId = process.env.RECEIPT_COSMOS_DB_NAME;
const receiptContainerId = process.env.RECEIPT_COSMOS_DB_CONTAINER_NAME;
const receiptErrorContainerId = process.env.RECEIPT_ERROR_COSMOS_DB_CONTAINER_NAME;
const receiptMessageContainerId = process.env.RECEIPT_MESSAGE_COSMOS_DB_CONTAINER_NAME;

const client = new CosmosClient(cosmos_db_conn_string);
const receiptContainer = client.database(databaseId).container(receiptContainerId);
const receiptErrorContainer = client.database(databaseId).container(receiptErrorContainerId);
const receiptMessageContainer = client.database(databaseId).container(receiptMessageContainerId);

//COSMOS BIZEVENT
const biz_cosmos_db_conn_string = process.env.BIZEVENTS_COSMOS_CONN_STRING;
const bizDatabaseId = process.env.BIZ_EVENT_COSMOS_DB_NAME;  // es. db
const bizContainerId = process.env.BIZ_EVENT_COSMOS_DB_CONTAINER_NAME; // es. biz-events

const bizClient = new CosmosClient(biz_cosmos_db_conn_string);
const bizContainer = bizClient.database(bizDatabaseId).container(bizContainerId);

//BLOB
const blobStorageConnString = process.env.RECEIPTS_STORAGE_CONN_STRING;
const blobStorageContainerName = process.env.BLOB_STORAGE_CONTAINER_NAME;

const blobServiceClient = BlobServiceClient.fromConnectionString(blobStorageConnString || "");
const containerClient = blobServiceClient.getContainerClient(blobStorageContainerName || "");


const deleteDocumentFromAllDatabases = async () => {
    let { resources } = await receiptContainer.items.query({
        query: "SELECT * from c WHERE c.eventData.debtorFiscalCode = @fiscalCode",
        parameters: [{ name: "@fiscalCode", value: TOKENIZED_FISCAL_CODE }]
    }).fetchAll();

    console.info(`Found n. ${resources?.length} receipts in the database`);

    for (const el of resources) {
        console.log("Cleaning documents linked to receipts with id: " + el.id);

        //Delete PDF from Blob Storage
        if (el?.mdAttach?.name?.length > 1) {
            let response = await containerClient.getBlockBlobClient(el.mdAttach.name).deleteIfExists();
            if (response._response.status !== 202) {
                console.error(`Error deleting PDF ${el.id}`);
            }
            console.log("RESPONSE DELETE PDF STATUS", response._response.status);
        }
        if (el?.mdAttachPayer?.name?.length > 1) {
            let response = await containerClient.getBlockBlobClient(el.mdAttachPayer.name).deleteIfExists();
            if (response._response.status !== 202) {
                console.error(`Error deleting PDF ${el.id}`);
            }
            console.log("RESPONSE DELETE PDF STATUS", response._response.status);
        }

        //Delete Receipt from CosmosDB
        try {
            await receiptContainer.item(el.id, el.id).delete();
        } catch (error) {
            if (error.code !== 404) {
                console.error(`Error deleting receipt ${el.id}`);
            }
        }

        //Delete Receipt error from CosmosDB
        try {
            let response = await receiptErrorContainer.items.query({
                query: "SELECT * from c WHERE c.bizEventId = @bizEventId",
                parameters: [{ name: "@bizEventId", value: el.eventId }]
            }).fetchAll();

            let resourcesError = response.resources;

            for (let error of resourcesError) {
                await receiptErrorContainer.item(error.id, error.id).delete();
            }
        } catch (error) {
            if (error.code !== 404) {
                console.error(`Error deleting receipt error ${el.eventId}`);
            }
        }

        //Delete Receipt message from CosmosDB
        try {
            let response = await receiptMessageContainer.items.query({
                query: "SELECT * from c WHERE c.eventId = @eventId",
                parameters: [{ name: "@eventId", value: el.eventId }]
            }).fetchAll();

            let resourcesMessage = response.resources;

            for (let message of resourcesMessage) {
                await receiptMessageContainer.item(message.id, message.id).delete();
            }
        } catch (error) {
            if (error.code !== 404) {
                console.error(`Error deleting receipt message ${el.eventId}`);
            }
        }

        //Delete BizEvent from CosmosDB
        try {
            await bizContainer.item(el.eventId, el.eventId).delete();
        } catch (error) {
            if (error.code !== 404) {
                console.error(`Error deleting bizevent ${el.eventId}`);
            }
        }

        console.log(`DONE ${el.id}`)
    }

};

deleteDocumentFromAllDatabases();