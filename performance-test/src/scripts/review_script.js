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
    let minTimeToInsert = 1000 * 60 * 60 * 24;
    let maxTimeToInsert = -1;

    let arrayTimeToGenerate = [];
    let totalTimeToGenerate = 0;
    let notGenerated = 0;
    let minTimeToGenerate = 1000 * 60 * 60 * 24;
    let maxTimeToGenerate = -1;

    let arrayTimeToNotify = [];
    let totalTimeToNotify = 0;
    let notNotified = 0;
    let minTimeToNotify = 1000 * 60 * 60 * 24;
    let maxTimeToNotify = -1;

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
                let timeToInsert = el.inserted_at - bizEvent._ts;

                arrayTimeToInsert.push(timeToInsert);
                totalTimeToInsert += timeToInsert;
                minTimeToInsert = timeToInsert < minTimeToInsert ? timeToInsert : minTimeToInsert;
                maxTimeToInsert = timeToInsert > maxTimeToInsert ? timeToInsert : maxTimeToInsert;

                if (arrayTimeToInsert.length === 1) {
                    console.log("TIMESTAMP BIZ", bizEvent._ts);
                    console.log("TIMESTAMP INSERTED", el.inserted_at);
                }
            }

            if (el.generated_at) {
                let timeToGenerate = el.generated_at - el.inserted_at;

                arrayTimeToGenerate.push(timeToGenerate);
                totalTimeToGenerate += timeToGenerate;
                minTimeToGenerate = timeToGenerate < minTimeToGenerate ? timeToGenerate : minTimeToGenerate;
                maxTimeToGenerate = timeToGenerate > maxTimeToGenerate ? timeToGenerate : maxTimeToGenerate;



                if (el.notified_at) {
                    let timeToNotify = el.notified_at - el.generated_at;

                    arrayTimeToNotify.push(timeToNotify);
                    totalTimeToNotify += timeToNotify;
                    minTimeToNotify = timeToNotify < minTimeToNotify ? timeToNotify : minTimeToNotify;
                    maxTimeToNotify = timeToNotify > maxTimeToNotify ? timeToNotify : maxTimeToNotify;

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
    console.log(`mean time to insert..............: ${totalTimeToInsert ? Math.round(totalTimeToInsert / arrayTimeToInsert.length) + "ms" : ""}`);
    console.log(`mean time to generate............: ${totalTimeToGenerate ? Math.round(totalTimeToGenerate / arrayTimeToGenerate.length) + "ms" : ""}`);
    console.log(`mean time to notify..............: ${totalTimeToNotify ? Math.round(totalTimeToNotify / arrayTimeToNotify.length) + "ms" : ""}`);
    console.log("--------------------------------");
    console.log(`min time to insert...............: ${minTimeToInsert}${minTimeToInsert ? "ms" : ""}`);
    console.log(`min time to generate.............: ${minTimeToGenerate}${minTimeToGenerate ? "ms" : ""}`);
    console.log(`min time to notify...............: ${minTimeToNotify}${minTimeToNotify ? "ms" : ""}`);
    console.log("--------------------------------");
    console.log(`max time to insert...............: ${maxTimeToInsert}${maxTimeToInsert ? "ms" : ""}`);
    console.log(`max time to generate.............: ${maxTimeToGenerate}${maxTimeToGenerate ? "ms" : ""}`);
    console.log(`max time to notify...............: ${maxTimeToNotify}${maxTimeToNotify ? "ms" : ""}`);
    console.log("--------------------------------");
    console.log(`p(95) time to insert.............: ${calculatePercentile(arrayTimeToInsert, 95, "ms")}`);
    console.log(`p(95) time to generate...........: ${calculatePercentile(arrayTimeToGenerate, 95, "ms")}`);
    console.log(`p(95) time to notify.............: ${calculatePercentile(arrayTimeToNotify, 95, "ms")}`);
    console.log("--------------------------------");
    console.log(`p(99) time to insert.............: ${calculatePercentile(arrayTimeToInsert, 99, "ms")}`);
    console.log(`p(99) time to generate...........: ${calculatePercentile(arrayTimeToGenerate, 99, "ms")}`);
    console.log(`p(99) time to notify.............: ${calculatePercentile(arrayTimeToNotify, 99, "ms")}`);
    console.log("--------------------------------");
    console.log(`p(99.99) time to insert..........: ${calculatePercentile(arrayTimeToInsert, 99.99, "ms")}`);
    console.log(`p(99.99) time to generate........: ${calculatePercentile(arrayTimeToGenerate, 99.99, "ms")}`);
    console.log(`p(99.99) time to notify..........: ${calculatePercentile(arrayTimeToNotify, 99.99, "ms")}`);
    console.log("\n\n");
    console.log("/////////////////////////////////");
    console.log("/------------- END -------------/");
    console.log("/////////////////////////////////");
}

reviewReceiptsTimeToProcess();