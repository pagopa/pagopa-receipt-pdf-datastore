const { CosmosClient } = require("@azure/cosmos");
const { createCartEvent } = require("./common");

const cosmos_db_conn_string = process.env.RECEIPTS_COSMOS_CONN_STRING || "";
const databaseId = process.env.RECEIPT_COSMOS_DB_NAME;
const receiptContainerId = process.env.RECEIPT_COSMOS_DB_CONTAINER_NAME;
const cartContainerId = process.env.RECEIPTS_COSMOS_CART_CONTAINER_NAME;

const client = new CosmosClient(cosmos_db_conn_string);
const receiptContainer = client.database(databaseId).container(receiptContainerId);
const cartContainer = client.database(databaseId).container(cartContainerId);

async function getDocumentByIdFromReceiptsDatastore(id) {
    return await receiptContainer.items
        .query({
            query: "SELECT * from c WHERE c.eventId=@eventId",
            parameters: [{ name: "@eventId", value: id }]
        })
        .fetchNext();
}

async function getCartDocumentByIdFromReceiptsDatastore(id) {
    return await cartContainer.items
        .query({
            query: "SELECT * from c WHERE c.eventId=@id",
            parameters: [{ name: "@id", value: id }]
        })
        .fetchNext();
}

async function deleteDocumentFromReceiptsDatastoreByEventId(eventId) {
    let documents = await getDocumentByIdFromReceiptsDatastore(eventId);

    documents?.resources?.forEach(el => {
        deleteDocumentFromReceiptsDatastore(el.id);
    })
}

async function deleteDocumentFromReceiptsDatastore(id) {
    try {
        return await receiptContainer.item(id, id).delete();
    } catch (error) {
        if (error.code !== 404) {
            console.log(error)
        }
    }
}

async function createDocumentInCartDatastore(id, listOfBizEventsIds) {
    let event = createCartEvent(id, listOfBizEventsIds);
    try {
        return await cartContainer.items.create(event);
    } catch (err) {
        console.log(err);
    }
}

async function deleteDocumentFromCartDatastore(id) {
    try {
        return await cartContainer.item(id, id).delete();
    } catch (error) {
        if (error.code !== 404) {
            console.log(error)
        }
    }
}

module.exports = {
    getDocumentByIdFromReceiptsDatastore, deleteDocumentFromReceiptsDatastoreByEventId, deleteDocumentFromReceiptsDatastore, createDocumentInCartDatastore, deleteDocumentFromCartDatastore, getCartDocumentByIdFromReceiptsDatastore
}