import { bizeventContainer, blobContainerClient, receiptContainer } from "./scripts_common.js";


//DELETE PDF FROM BLOB STORAGE
const deleteDocumentFromAllDatabases = async () => {
    let r = await receiptContainer.items.query(`SELECT * from c WHERE c.eventData.debtorFiscalCode = ${SIM_TEST_CF}`).fetchAll();
    
    let receipts = r?.resources;

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
            await receiptContainer.item(el.id).delete();
        } catch (error) {
            if (error.code !== 404) {
                console.error(`Error deleting receipt ${el.id}`);
            }
        }

        //Delete BizEvent from CosmosDB
        try {
            await bizeventContainer.item(el.eventId).delete();
        } catch (error) {
            if (error.code !== 404) {
                console.error(`Error deleting receipt ${el.id}`);
            }
        }


    });

};

deleteDocumentFromAllDatabases();
