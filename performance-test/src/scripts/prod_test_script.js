import { CosmosClient } from '@azure/cosmos';

//ENVIRONMENTAL VARIABLES
const bizEventCosmosDBConnString = ""; //bizEvent cosmos connection string
const bizEventDatabaseID = "db";
const bizEventContainerID = "biz-events";
const receiptCosmosDBConnString = ""; //receipt cosmos connection string
const receiptDatabaseID = "db";
const receiptContainerID = "receipts";
const apiRetryFailedURI = ""; //Retry failed function apim uri
const ocpApimKey = ""; //APIM ocp key

//CLIENTS
const bizeventClient = new CosmosClient(bizEventCosmosDBConnString);
const bizeventContainer = bizeventClient.database(bizEventDatabaseID).container(bizEventContainerID);

const receiptClient = new CosmosClient(receiptCosmosDBConnString);
const receiptContainer = receiptClient.database(receiptDatabaseID).container(receiptContainerID);

//INPUTS
const minDate = new Date("04/01/2023").getTime(); //mm/dd/yyyy
const FISCAL_CODES_TO_TEST = [
    ""
];

const updateBizEvents = async () => {
    for (const fiscalCode of FISCAL_CODES_TO_TEST) {
        let { resources } = await bizeventContainer.items.query({
            query: "SELECT * from c WHERE c.debtor.entityUniqueIdentifierValue = @fiscalCode and c.timestamp >= @minDate and c.eventStatus = 'DONE'",
            parameters: [{ name: "@fiscalCode", value: fiscalCode }, { name: "@minDate", value: minDate }]
        }).fetchAll();

        for (let j = 0; j < resources.length; j++) {
            let bizEvent = resources[j];

            let receiptResponse = await receiptContainer.items.query({
                query: "SELECT * from c WHERE c.eventId = @eventId",
                parameters: [{ name: "@eventId", value: bizEvent.id }]
            }).fetchAll();
            let receiptResources = receiptResponse?.resources;

            if (receiptResources?.length === 0) {
                let bizEventExists = await bizeventContainer.item(bizEvent.id, bizEvent.id).read();

                if (bizEventExists.statusCode === 200) {
                    let receipt = {
                        eventId: bizEvent.id,
                        status: "FAILED"
                    };
                    let { resource } = await receiptContainer.items.create(receipt);
                    console.log("SAVED receipt with id: " + resource.id);

                    await fetch(apiRetryFailedURI, {
                        method: "PUT",
                        headers: {
                            'Content-Type': 'application/json',
                            "Ocp-Apim-Subscription-Key": ocpApimKey
                        },
                        body: JSON.stringify({
                            "eventId": bizEvent.id
                        })
                    })
                        .then((res) => {
                            console.log("RETRIED receipt with id: " + resource.id, res.status);
                        })
                        .catch(() => console.log("FAILED RETRY for receipt with id: " + resource.id))
                }

            } else {
                console.log("ABORTED too many receipt with bizEventId: " + bizEvent.id);
            }
        }
    }
}

updateBizEvents();