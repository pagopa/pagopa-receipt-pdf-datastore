const assert = require('assert');
const { After, Given, When, Then, setDefaultTimeout } = require('@cucumber/cucumber');
const { sleep } = require("./common");
const { createDocumentInBizEventsDatastore, deleteDocumentFromBizEventsDatastore } = require("./biz_events_datastore_client");
const { getDocumentByIdFromReceiptsDatastore, deleteDocumentFromErrorReceiptsDatastoreByMessagePayload, deleteDocumentFromReceiptsDatastoreByEventId, deleteDocumentFromReceiptsDatastore, createDocumentInReceiptsDatastore, createDocumentInErrorReceiptsDatastore, deleteDocumentFromErrorReceiptsDatastore, getDocumentByMessagePayloadFromErrorReceiptsDatastore } = require("./receipts_datastore_client");

// set timeout for Hooks function, it allows to wait for long task
setDefaultTimeout(360 * 1000);

// initialize variables
this.eventId = null;
this.responseToCheck = null;
this.receiptId = null;
this.errorReceiptId = null;
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
    if (this.errorReceiptId != null) {
        await deleteDocumentFromErrorReceiptsDatastore(this.errorReceiptId);
    }
    this.eventId = null;
    this.responseToCheck = null;
    this.receiptId = null;
    this.errorReceiptId = null;
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

Given('a receipt with id {string} stored into receipt datastore', async function (id) {
    this.eventId = id;
    // prior cancellation to avoid dirty cases
    await deleteDocumentFromReceiptsDatastore(this.eventId, this.eventId);

    let receiptsStoreResponse = await createDocumentInReceiptsDatastore(this.eventId);
    assert.strictEqual(receiptsStoreResponse.statusCode, 201);
    this.receiptId = this.eventId;
});


Given('a error receipt with id {string} stored into receipt-message-error datastore with status REVIEWED', async function (id) {
    assert.strictEqual(this.eventId, id);
    let response = await createDocumentInErrorReceiptsDatastore(id);
    assert.strictEqual(response.statusCode, 201);
    this.errorReceiptId = id;
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




