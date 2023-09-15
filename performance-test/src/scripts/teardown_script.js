import { bizEventContainerID, bizEventCosmosDBPrimaryKey, bizEventCosmosDBURI, bizEventDatabaseID, blobContainerClient, receiptContainerID, receiptCosmosDBPrimaryKey, receiptCosmosDBURI, receiptDatabaseID } from "./scripts_common.js";
import { getDocumentByDebtorCF, deleteDocument } from "../modules/datastore_client.js";


//DELETE PDF FROM BLOB STORAGE
const deleteDocumentFromAllDatabases = async () => {
    let r = getDocumentByDebtorCF(receiptCosmosDBURI, receiptDatabaseID, receiptContainerID, receiptCosmosDBPrimaryKey, SIM_TEST_CF);
    
    let receipts = r?.json()?.Documents;

    console.info(`Found n. ${receipts?.length} receipts in the database`);

    receipts?.forEach(async (el) => {
        //Delete PDF from Blob Storage
        const response = await blobContainerClient.deleteBlob(el.id);
        if (response._response.status !== 202) {
            console.error(`Error deleting PDF ${el.id}`);
        }

        response.then((res) => {
            console.log("RESPONSE DELETE PDF STATUS", res._response.status);
        })

        //Delete Receipt from CosmosDB
        try {
            deleteDocument(receiptCosmosDBURI, receiptDatabaseID, receiptContainerID, receiptCosmosDBPrimaryKey, el.id).then(resp => {
                console.info("RESPONSE DELETE RECEIPT STATUS", resp.statusCode);
            });
        } catch (error) {
            if (error.code !== 404) {
                console.error(`Error deleting receipt ${el.id}`);
            }
        }

        //Delete BizEvent from CosmosDB
        try {
            deleteDocument(bizEventCosmosDBURI, bizEventDatabaseID, bizEventContainerID, bizEventCosmosDBPrimaryKey, el.eventId).then(resp => {
                console.info("RESPONSE DELETE BIZEVENT STATUS", resp.statusCode);
            });
        } catch (error) {
            if (error.code !== 404) {
                console.error(`Error deleting receipt ${el.id}`);
            }
        }


    });

};

deleteDocumentFromAllDatabases();
