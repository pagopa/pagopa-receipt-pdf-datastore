import { getDocumentByDebtorCF, getDocumentById } from "../modules/datastore_client";
import { bizEventContainerID, bizEventCosmosDBPrimaryKey, bizEventCosmosDBURI, bizEventDatabaseID, receiptContainerID, receiptCosmosDBPrimaryKey, receiptCosmosDBURI, receiptDatabaseID } from "./scripts_common";

const reviewReceiptsTimeToProcess = () => {
    let r = getDocumentByDebtorCF(receiptCosmosDBURI, receiptDatabaseID, receiptContainerID, receiptCosmosDBPrimaryKey, SIM_TEST_CF);

    let receipts = r?.json()?.Documents;

    let arrayTimeToInsert = [];
    let totalTimeToInsert = 0;
    let notInserted = 0;
    let minTimeToInsert = -1;
    let maxTimeToInsert = -1;

    let arrayTimeToGenerate = [];
    let totalTimeToGenerate = 0;
    let notGenerated = 0;
    let minTimeToGenerate = -1;
    let maxTimeToGenerate = -1;

    let arrayTimeToNotify = [];
    let totalTimeToNotify = 0;
    let notNotified = 0;
    let minTimeToNotify = -1;
    let maxTimeToNotify = -1;

    let receiptsCompleted = 0;

    receipts?.forEach((el) => {

        if(el.inserted_at){
            let bizEvent = getDocumentById(bizEventCosmosDBURI, bizEventDatabaseID, bizEventContainerID, bizEventCosmosDBPrimaryKey, el.eventId)?.json()?.Documents?.[0];

            if(bizEvent?._ts){
                let timeToInsert = el.inserted_at - bizEvent._ts;

                arrayTimeToInsert.push(timeToInsert);
                totalTimeToInsert += timeToInsert;
                minTimeToInsert = (minTimeToInsert === -1 || timeToInsert < minTimeToInsert) ? timeToInsert : minTimeToInsert;
                maxTimeToInsert = timeToInsert > maxTimeToInsert ? timeToInsert : maxTimeToInsert;
            }
            
            if(el.generated_at){
                let timeToGenerate = el.generated_at - el.inserted_at;

                arrayTimeToGenerate.push(timeToGenerate);
                totalTimeToGenerate += timeToGenerate;
                minTimeToGenerate = (minTimeToGenerate === -1 || timeToGenerate < minTimeToGenerate) ? timeToGenerate : minTimeToGenerate;
                maxTimeToGenerate = timeToGenerate > maxTimeToGenerate ? timeToGenerate : maxTimeToGenerate;

                if(el.notified_at){
                    let timeToNotify = el.notified_at - el.generated_at;
        
                    arrayTimeToNotify.push(timeToNotify);
                    totalTimeToNotify += timeToNotify;
                    minTimeToNotify = (minTimeToNotify === -1 || timeToNotify < minTimeToNotify) ? timeToNotify : minTimeToNotify;
                    maxTimeToNotify = timeToNotify > maxTimeToNotify ? timeToNotify : maxTimeToNotify;

                    receiptsCompleted += 1;
                } else {
                    notNotified += 1;
                }

            } else {
                notGenerated += 1;
            }

        } else {
            notInserted += 1;
        }
    })

    console.log("/////////////////////////////////");
    console.log("/----------- METRICS -----------/");
    console.log("/////////////////////////////////");
    console.log(" ");
    console.log(`total receipts...................: ${receipts?.length ?? 0}`);
    console.log(`receipts processed completely....: ${receiptsCompleted}`);
    console.log(`receipts failed to complete......: ${notInserted + notGenerated + notNotified}`);
    console.log("--------------------------------");
    console.log(`receipts inserted................: ${arrayTimeToInsert.length}`);
    console.log(`receipts generated...............: ${arrayTimeToGenerate.length}`);
    console.log(`receipts notified................: ${arrayTimeToNotify.length}`);
    console.log("--------------------------------");
    console.log(`receipts failed to be inserted...: ${notInserted}`);
    console.log(`receipts failed to be generated..: ${notGenerated}`);
    console.log(`receipts failed to be notified...: ${notNotified}`);
    console.log("--------------------------------");
    console.log(`mean time to insert..............: ${totalTimeToInsert/arrayTimeToInsert.length}`);
    console.log(`mean time to generate............: ${totalTimeToGenerate/arrayTimeToGenerate.length}`);
    console.log(`mean time to notify..............: ${totalTimeToNotify/arrayTimeToNotify.length}`);
    console.log("--------------------------------");
    console.log(`min time to insert..............: ${minTimeToInsert}`);
    console.log(`min time to generate..............: ${minTimeToGenerate}`);
    console.log(`min time to notify..............: ${minTimeToNotify}`);
    console.log("--------------------------------");
    console.log(`max time to insert..............: ${maxTimeToInsert}`);
    console.log(`max time to generate..............: ${maxTimeToGenerate}`);
    console.log(`max time to notify..............: ${maxTimeToNotify}`);
    console.log(" ");
    console.log("/////////////////////////////////");
    console.log("/------------- END -------------/");
    console.log("/////////////////////////////////");
    
}

reviewReceiptsTimeToProcess();