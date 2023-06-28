const { CosmosClient } = require("@azure/cosmos");
const {createEvent} = require("./common");

const cosmos_db_conn_string  = process.env.BIZEVENTS_COSMOS_CONN_STRING;
const databaseId             = process.env.BIZ_EVENT_COSMOS_DB_NAME;  // es. db
const containerId            = process.env.BIZ_EVENT_COSMOS_DB_CONTAINER_NAME; // es. biz-events

console.log("BIZEVENTS_COSMOS_CONN_STRING " + cosmos_db_conn_string)
console.log("RECEIPTS_STORAGE_CONN_STRING " + process.env.RECEIPTS_STORAGE_CONN_STRING)
console.log("RECEIPTS_COSMOS_CONN_STRING " + process.env.RECEIPTS_COSMOS_CONN_STRING)
console.log("BIZ_EVENT_COSMOS_DB_NAME " + databaseId)
console.log("BIZ_EVENT_COSMOS_DB_CONTAINER_NAME " + containerId)
const client = new CosmosClient(cosmos_db_conn_string);
const container = client.database(databaseId).container(containerId);

async function getDocumentByIdFromBizEventsDatastore(id) {
    return await container.items
                    .query({
                        query: "SELECT * from c WHERE c.id=@id",
                        parameters: [{ name: "@id", value: id }]
                      })
                    .fetchAll();
}

async function createDocumentInBizEventsDatastore(id, status) {  
    let event = createEvent(id, status);
    try {
        return await container.items.create(event);
    } catch (err) {
        console.log(err);
    }
}

async function deleteDocumentFromBizEventsDatastore(id) {
    try {
        return await container.item(id, id).delete();
    } catch (error) {
        if (error.code !== 404) {
            console.log(error)
        }
    }
}

module.exports = {
    getDocumentByIdFromBizEventsDatastore, createDocumentInBizEventsDatastore, deleteDocumentFromBizEventsDatastore
}