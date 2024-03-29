import { TOKENIZED_SIM_TEST_CF } from "../modules/common.js";
import { bizeventContainer, blobContainerClient, receiptContainer } from "./scripts_common.js";


//DELETE PDF FROM BLOB STORAGE
const deleteDocumentFromAllDatabases = async () => {
    let { resources } = await receiptContainer.items.query({
        query: "SELECT * from c WHERE c.eventData.debtorFiscalCode = @fiscalCode",
        parameters: [{ name: "@fiscalCode", value: TOKENIZED_SIM_TEST_CF }]
    }).fetchAll();

    console.info(`Found n. ${resources?.length} receipts in the database`);

    for (const el of resources) {
        console.log("Cleaning documents linked to receipts with id: " + el.id);

        //Delete PDF from Blob Storage
        if (el?.mdAttach?.name?.length > 1) {
            let response = await blobContainerClient.getBlockBlobClient(el.mdAttach.name).deleteIfExists();
            if (response._response.status !== 202) {
                console.error(`Error deleting PDF ${el.id}`);
            }
            console.log("RESPONSE DELETE PDF STATUS", response._response.status);
        }
        if (el?.mdAttachPayer?.name?.length > 1) {
            let response = await blobContainerClient.getBlockBlobClient(el.mdAttachPayer.name).deleteIfExists();
            if (response._response.status !== 202) {
                console.error(`Error deleting PDF ${el.id}`);
            }
            console.log("RESPONSE DELETE PDF STATUS", response._response.status);
        }

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
                console.error(`Error deleting bizevent ${el.eventId}`);
            }
        }
    }

};

deleteDocumentFromAllDatabases();
