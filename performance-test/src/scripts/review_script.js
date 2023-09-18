import { bizeventContainer, receiptContainer } from "./scripts_common.js";
import { SIM_TEST_CF } from '../modules/common.js';

function calculatePercentile(array, percentile, suffix) {
    const currentIndex = 0;
    const totalCount = array.reduce((count, currentValue) => {
        if (currentValue < percentile) {
            return count + 1; // add 1 to `count`
        } else if (currentValue === percentile) {
            return count + 0.5; // add 0.5 to `count`
        }
        return count + 0;
    }, currentIndex);
    let fin = (totalCount * 100) / array.length;
    return `${fin}${fin ? (suffix || "") : ""}`;
}

const reviewReceiptsTimeToProcess = async () => {
    let arrayTimeToInsert = [];
    let totalTimeToInsert = 0;
    let notInserted = 0;
    let minTimeToInsert = null;
    let maxTimeToInsert = null;

    let arrayTimeToGenerate = [];
    let totalTimeToGenerate = 0;
    let notGenerated = 0;
    let minTimeToGenerate = null;
    let maxTimeToGenerate = null;

    let arrayTimeToNotify = [];
    let totalTimeToNotify = 0;
    let notNotified = 0;
    let minTimeToNotify = null;
    let maxTimeToNotify = null;

    let receiptsCompleted = 0;

    let { resources } = await receiptContainer.items.query({
        query: "SELECT * from c WHERE c.eventData.debtorFiscalCode = @fiscalCode",
        parameters: [{ name: "@fiscalCode", value: SIM_TEST_CF }]
    }).fetchAll();

    for (const el of resources) {
        console.log("Processing receipt with id: " + el.id);
        if (el.inserted_at) {
            let bizEventResponse = await bizeventContainer.item(el.eventId, el.eventId).read();
            let bizEvent = bizEventResponse.resource;

            if (bizEvent?._ts > 0) {
                //BizEvent ts is in seconds
                let timeToInsert = el.inserted_at - (bizEvent._ts * 1000);

                arrayTimeToInsert.push(timeToInsert);
                totalTimeToInsert += timeToInsert;
                minTimeToInsert = minTimeToInsert === null || timeToInsert < minTimeToInsert ? timeToInsert : minTimeToInsert;
                maxTimeToInsert = maxTimeToInsert === null || timeToInsert > maxTimeToInsert ? timeToInsert : maxTimeToInsert;
            }

            if (el.generated_at) {
                let timeToGenerate = el.generated_at - el.inserted_at;

                arrayTimeToGenerate.push(timeToGenerate);
                totalTimeToGenerate += timeToGenerate;
                minTimeToGenerate = minTimeToGenerate === null || timeToGenerate < minTimeToGenerate ? timeToGenerate : minTimeToGenerate;
                maxTimeToGenerate = maxTimeToGenerate === null || timeToGenerate > maxTimeToGenerate ? timeToGenerate : maxTimeToGenerate;



                if (el.notified_at) {
                    let timeToNotify = el.notified_at - el.generated_at;

                    arrayTimeToNotify.push(timeToNotify);
                    totalTimeToNotify += timeToNotify;
                    minTimeToNotify = minTimeToNotify === null || timeToNotify < minTimeToNotify ? timeToNotify : minTimeToNotify;
                    maxTimeToNotify = maxTimeToNotify === null || timeToNotify > maxTimeToNotify ? timeToNotify : maxTimeToNotify;

                    receiptsCompleted += 1;
                } else {
                    notNotified += 1;
                }

            } else {
                notGenerated += 1;
                notNotified += 1;
            }

        } else {
            notInserted += 1;
        }
    }

    console.log("/////////////////////////////////");
    console.log("/----------- METRICS -----------/");
    console.log("/////////////////////////////////");
    console.log("\n\n");
    console.log(`total receipts...................: ${resources?.length ?? 0}`);
    console.log(`receipts processed completely....: ${receiptsCompleted}`);
    console.log(`receipts failed to complete......: ${notNotified}`);
    console.log("--------------------------------");
    console.log(`receipts inserted................: ${arrayTimeToInsert.length}`);
    console.log(`receipts generated...............: ${arrayTimeToGenerate.length}`);
    console.log(`receipts notified................: ${arrayTimeToNotify.length}`);
    console.log("--------------------------------");
    console.log(`receipts failed to be inserted...: ${notInserted}`);
    console.log(`receipts failed to be generated..: ${notGenerated}`);
    console.log(`receipts failed to be notified...: ${notNotified}`);
    console.log("--------------------------------");
    console.log(`mean time to insert..............: ${totalTimeToInsert ? `${Math.round(totalTimeToInsert / arrayTimeToInsert.length)}ms | ${Math.round(totalTimeToInsert / arrayTimeToInsert.length) / 1000}s` : ""}`);
    console.log(`mean time to generate............: ${totalTimeToGenerate ? `${Math.round(totalTimeToGenerate / arrayTimeToGenerate.length)}ms | ${Math.round(totalTimeToGenerate / arrayTimeToGenerate.length) / 1000}s` : ""}`);
    console.log(`mean time to notify..............: ${totalTimeToNotify ? `${Math.round(totalTimeToNotify / arrayTimeToNotify.length)}ms | ${Math.round(totalTimeToNotify / arrayTimeToNotify.length) / 1000}s` : ""}`);
    console.log("--------------------------------");
    console.log(`min time to insert...............: ${minTimeToInsert ? `${minTimeToInsert}ms | ${minTimeToInsert / 1000}s` : ""}`);
    console.log(`min time to generate.............: ${minTimeToGenerate ? `${minTimeToGenerate}ms | ${minTimeToGenerate / 1000}s` : ""}`);
    console.log(`min time to notify...............: ${minTimeToNotify ? `${minTimeToNotify}ms | ${minTimeToNotify / 1000}s` : ""}`);
    console.log("--------------------------------");
    console.log(`max time to insert...............: ${maxTimeToInsert ? `${maxTimeToInsert}ms | ${maxTimeToInsert / 1000}s` : ""}`);
    console.log(`max time to generate.............: ${maxTimeToGenerate ? `${maxTimeToGenerate}ms | ${maxTimeToGenerate / 1000}s` : ""}`);
    console.log(`max time to notify...............: ${maxTimeToNotify ? `${maxTimeToNotify}ms | ${maxTimeToNotify / 1000}s` : ""}`);
    console.log("\n\n");
    console.log("/////////////////////////////////");
    console.log("/------------- END -------------/");
    console.log("/////////////////////////////////");
}

reviewReceiptsTimeToProcess();