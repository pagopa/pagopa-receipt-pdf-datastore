import { getDocumentByDebtorCF, getDocumentById } from "../modules/datastore_client";
import { bizEventContainerID, bizEventCosmosDBPrimaryKey, bizEventCosmosDBURI, bizEventDatabaseID, receiptContainerID, receiptCosmosDBPrimaryKey, receiptCosmosDBURI, receiptDatabaseID } from "./scripts_common";

function calculatePercentile(array, percentile){
    const currentIndex = 0;
    const totalCount = array.reduce((count, currentValue) => {
      if (currentValue < percentile) {
        return count + 1; // add 1 to `count`
      } else if (currentValue === percentile) {
        return count + 0.5; // add 0.5 to `count`
      }
      return count + 0;
    }, currentIndex);
    return (totalCount * 100) / array.length;
}

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
    console.log("\n\n");
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
    console.log(`mean time to insert..............: ${totalTimeToInsert/arrayTimeToInsert.length}ms`);
    console.log(`mean time to generate............: ${totalTimeToGenerate/arrayTimeToGenerate.length}ms`);
    console.log(`mean time to notify..............: ${totalTimeToNotify/arrayTimeToNotify.length}ms`);
    console.log("--------------------------------");
    console.log(`min time to insert...............: ${minTimeToInsert}ms`);
    console.log(`min time to generate.............: ${minTimeToGenerate}ms`);
    console.log(`min time to notify...............: ${minTimeToNotify}ms`);
    console.log("--------------------------------");
    console.log(`max time to insert...............: ${maxTimeToInsert}ms`);
    console.log(`max time to generate.............: ${maxTimeToGenerate}ms`);
    console.log(`max time to notify...............: ${maxTimeToNotify}ms`);
    console.log("--------------------------------");
    console.log(`p(95) time to insert.............: ${calculatePercentile(arrayTimeToInsert, 95)}ms`); 
    console.log(`p(95) time to generate...........: ${calculatePercentile(arrayTimeToGenerate, 95)}ms`); 
    console.log(`p(95) time to notify.............: ${calculatePercentile(arrayTimeToNotify, 95)}ms`); 
    console.log("--------------------------------");
    console.log(`p(99) time to insert.............: ${calculatePercentile(arrayTimeToInsert, 99)}ms`); 
    console.log(`p(99) time to generate...........: ${calculatePercentile(arrayTimeToGenerate, 99)}ms`); 
    console.log(`p(99) time to notify.............: ${calculatePercentile(arrayTimeToNotify, 99)}ms`); 
    console.log("--------------------------------");
    console.log(`p(99.99) time to insert..........: ${calculatePercentile(arrayTimeToInsert, 99.99)}ms`); 
    console.log(`p(99.99) time to generate........: ${calculatePercentile(arrayTimeToGenerate, 99.99)}ms`); 
    console.log(`p(99.99) time to notify..........: ${calculatePercentile(arrayTimeToNotify, 99.99)}ms`); 
    console.log("\n\n");
    console.log("/////////////////////////////////");
    console.log("/------------- END -------------/");
    console.log("/////////////////////////////////");
}

reviewReceiptsTimeToProcess();