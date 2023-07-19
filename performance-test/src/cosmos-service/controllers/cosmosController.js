import { CosmosClient } from "@azure/cosmos";

function produceCosmosClient(endpoint, key){
    return new CosmosClient({
        endpoint,
        key
    });
}

export async function createDocument(req, res){
    let {endpoint, databaseId, containerId, key, document} = req.body;

    let cosmosClient = produceCosmosClient(endpoint, key);

    const database = cosmosClient.database(databaseId);
    const container = database.container(containerId);

    let formattedResponse = {};

    try{
        let response = await container.items.create(document);

        console.log("Created document with id "+ document.id);

        formattedResponse = {
            status: response.statusCode,
        }
    } catch(e){
        console.error("Failed to create document with id "+ document.id);
        console.error("Error code and message: "+ e.code + " , "+ e.message);

        formattedResponse = {
            status: e.code,
            message: e.message
        }
    }

    res.json(formattedResponse);
}

export async function getDocumentByEventId(req, res){
    let {endpoint, databaseId, containerId, key, eventId} = req.body;

    let cosmosClient = produceCosmosClient(endpoint, key);

    const database = cosmosClient.database(databaseId);
    const container = database.container(containerId);

    const querySpec = {
        query: "select * from products p where p.eventId=@eventId",
        parameters: [
            {
                name: "@eventId",
                value: eventId
            }
        ]
    };

    let formattedResponse = {};

    try{
        const {resources} = await container.items.query(querySpec).fetchAll();

        console.log("Retrieved document with eventId "+ eventId);

        formattedResponse = {
            status: 200,
            documents: resources
        }
    } catch(e){
        console.error("Failed to retrieve document with eventId "+ eventId);
        console.error("Error code and message: "+ e.code + " , "+ e.message);

        formattedResponse = {
            status: e.code,
            message: e.message
        }
    }

    res.json(formattedResponse);
}

export async function deleteDocument(req, res){
    let {endpoint, databaseId, containerId, key, id, eventId} = req.body;

    let cosmosClient = produceCosmosClient(endpoint, key);

    const database = cosmosClient.database(databaseId);
    const container = database.container(containerId);

    let formattedResponse = {};

    try{
        const { statusCode } = await container.item(id, eventId).delete();

        console.log("Deleted document with id "+ id);

        formattedResponse = {
            status: statusCode
        }
    } catch(e){
        console.error("Failed to delete document with id "+ id);
        console.error("Error code and message: "+ e.code + " , "+ e.message);

        formattedResponse = {
            status: e.code,
            message: e.message
        }
    }

    res.json(formattedResponse);
}