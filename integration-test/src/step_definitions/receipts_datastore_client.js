const { CosmosClient } = require("@azure/cosmos");
const { createReceipt, createEventForPoisonQueue } = require("./common");

const cosmos_db_conn_string     = process.env.RECEIPTS_COSMOS_CONN_STRING || "";
const databaseId                = process.env.RECEIPT_COSMOS_DB_NAME;
const receiptContainerId        = process.env.RECEIPT_COSMOS_DB_CONTAINER_NAME;
const errorReceiptContainerId   = process.env.RECEIPT_MESSAGE_ERRORS_COSMOS_DB_CONTAINER_NAME;

const client = new CosmosClient(cosmos_db_conn_string);
const receiptContainer = client.database(databaseId).container(receiptContainerId);
const errorReceiptContainer = client.database(databaseId).container(errorReceiptContainerId);

async function getDocumentByIdFromReceiptsDatastore(id) {
    return await receiptContainer.items
        .query({
            query: "SELECT * from c WHERE c.eventId=@eventId",
            parameters: [{ name: "@eventId", value: id }]
        })
        .fetchNext();
}

async function deleteDocumentFromReceiptsDatastoreByEventId(eventId){
    let documents = await getDocumentByIdFromReceiptsDatastore(eventId);

    documents?.resources?.forEach(el => {
        deleteDocumentFromReceiptsDatastore(el.id, eventId);
    })
}

async function createDocumentInReceiptsDatastore(id) {
    let event = createReceipt(id);
    try {
        return await receiptContainer.items.create(event);
    } catch (err) {
        console.log(err);
    }
}

async function deleteDocumentFromReceiptsDatastore(id, partitionKey) {
    try {
        return await receiptContainer.item(id, partitionKey).delete();
    } catch (error) {
        if (error.code !== 404) {
            console.log(error)
        }
    }
}

// receipts-message-error datastore

async function getDocumentByMessagePayloadFromErrorReceiptsDatastore(messagePayload) {
    return await errorReceiptContainer.items
        .query({
            query: "SELECT * from c WHERE c.messagePayload=@messagePayload",
            parameters: [{ name: "@messagePayload", value: JSON.stringify(messagePayload) }]
        })
        .fetchNext();
}

async function createDocumentInErrorReceiptsDatastore(id) {
    let event = createEventForPoisonQueue(id, true);
    let payload = {
        "messagePayload": JSON.stringify(event),
        "status": "REVIEWED",
        "id": id,
        "_rid": "Z9AJAJpW0pIhAAAAAAAAAA==",
        "_self": "dbs/Z9AJAA==/colls/Z9AJAJpW0pI=/docs/Z9AJAJpW0pIhAAAAAAAAAA==/",
        "_etag": "\"7e005a10-0000-0d00-0000-64a27a780000\"",
        "_attachments": "attachments/",
        "_ts": 1688369784
    };
    try {
        return await errorReceiptContainer.items.create(payload);
    } catch (err) {
        console.log(err);
    }
}

async function deleteDocumentFromErrorReceiptsDatastore(id) {
    try {
        return await errorReceiptContainer.item(id, id).delete();
    } catch (error) {
        if (error.code !== 404) {
            console.log(error)
        }
    }
}

async function deleteDocumentFromErrorReceiptsDatastoreByMessagePayload(messagePayload) {
    let documents = await getDocumentByMessagePayloadFromErrorReceiptsDatastore(messagePayload);

    documents?.resources?.forEach((el) => {
        deleteDocumentFromErrorReceiptsDatastore(el.id);
    })
}

module.exports = {
    getDocumentByIdFromReceiptsDatastore, deleteDocumentFromReceiptsDatastoreByEventId, createDocumentInReceiptsDatastore, deleteDocumentFromReceiptsDatastore, getDocumentByMessagePayloadFromErrorReceiptsDatastore, createDocumentInErrorReceiptsDatastore, deleteDocumentFromErrorReceiptsDatastore, deleteDocumentFromErrorReceiptsDatastoreByMessagePayload
}