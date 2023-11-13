const assert = require('assert');
const { After, Given, When, Then, setDefaultTimeout } = require('@cucumber/cucumber');
const { sleep, recoverFailedEvent, updateReceiptToFailed } = require("./common");
const { createDocumentInBizEventsDatastore, deleteDocumentFromBizEventsDatastore } = require("./biz_events_datastore_client");
const { getDocumentByIdFromReceiptsDatastore, deleteDocumentFromReceiptsDatastoreByEventId, deleteDocumentFromReceiptsDatastore, updateReceiptToFailed } = require("./receipts_datastore_client");

// set timeout for Hooks function, it allows to wait for long task
setDefaultTimeout(360 * 1000);

// initialize variables
this.eventId = null;
this.responseToCheck = null;
this.receiptId = null;
this.event = null;

// After each Scenario
After(async function () {
    // remove event
    if (this.eventId != null) {
        await deleteDocumentFromBizEventsDatastore(this.eventId);
    }
    if (this.eventId != null && this.receiptId != null) {
        await deleteDocumentFromReceiptsDatastore(this.receiptId, this.eventId);
    }
    this.eventId = null;
    this.responseToCheck = null;
    this.receiptId = null;
    this.event = null;
});

Given('a random biz event with id {string} stored on biz-events datastore with status DONE', async function (id) {
    this.eventId = id;
    // prior cancellation to avoid dirty cases
    await deleteDocumentFromBizEventsDatastore(this.eventId);
    await deleteDocumentFromReceiptsDatastoreByEventId(this.eventId);

    let bizEventStoreResponse = await createDocumentInBizEventsDatastore(this.eventId);
    assert.strictEqual(bizEventStoreResponse.statusCode, 201);
});

When('biz event has been properly stored into receipt datastore after {int} ms with eventId {string}', async function (time, eventId) {
    // boundary time spent by azure function to process event
    await sleep(time);
    this.responseToCheck = await getDocumentByIdFromReceiptsDatastore(eventId);
});

Then('the receipts datastore returns the receipt', async function () {
    assert.notStrictEqual(this.responseToCheck.resources.length, 0);
    this.receiptId = this.responseToCheck.resources[0].id;
    assert.strictEqual(this.responseToCheck.resources.length, 1);
});

Then('the receipt has eventId {string}', function (targetId) {
    assert.strictEqual(this.responseToCheck.resources[0].eventId, targetId);
});

Then('the receipt has not the status {string}', function (targetStatus) {
    assert.notStrictEqual(this.responseToCheck.resources[0].status, targetStatus);
});

Given('a random receipt with id {string} stored with status FAILED', async function (id) {
    this.eventId = id;
    // prior cancellation to avoid dirty cases
    await updateReceiptToFailed(this.eventId);
});

When('HTTP recovery request is called', async function (time, eventId) {
    // boundary time spent by azure function to process event
    await sleep(time);
    this.responseToCheck = await recoverFailedEvent(eventId);
});

Then('the receipt has not the status {string} after {int} ms', function (targetStatus, time) {
    await sleep(time);
    this.responseToCheck = await getDocumentByIdFromReceiptsDatastore(eventId);
    assert.notStrictEqual(this.responseToCheck.resources[0].status, targetStatus);
});





