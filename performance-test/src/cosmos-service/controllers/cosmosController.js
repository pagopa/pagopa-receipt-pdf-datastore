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

        formattedResponse = {
            status: response.statusCode,
        }
    } catch(e){
        formattedResponse = {
            status: e.code,
            message: e.message
        }
    }

    res.json(formattedResponse);
}