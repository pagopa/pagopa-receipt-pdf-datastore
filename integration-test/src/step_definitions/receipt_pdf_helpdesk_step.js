const assert = require('assert');
const { After, Given, When, Then, setDefaultTimeout } = require('@cucumber/cucumber');
const { 
    createDocumentInBizEventsDatastore, 
    deleteDocumentFromBizEventsDatastore } = require("../../src/step_definitions/biz_events_datastore_client");
const {
    deleteDocumentFromReceiptsDatastore,
    createDocumentInReceiptsDatastore,
    createDocumentInReceiptErrorDatastore,
    deleteDocumentFromReceiptErrorDatastore,
    getDocumentFromReceiptsErrorDatastoreByBizEventId,
    getDocumentFromReceiptsDatastoreByEventIdOrdered,
    deleteMultipleDocumentFromReceiptErrorDatastoreByEventId,
    deleteDocumentFromReceiptsDatastoreByEventId
} = require("../../src/step_definitions/receipts_datastore_client");
const {
    postReceiptToReviewed,
    postRecoverFailedReceipt,
    postRecoverFailedReceiptMassive,
    postRecoverNotNotifiedReceipt,
    postRecoverNotNotifiedReceiptMassive,
} = require("../../src/step_definitions/api_helpdesk_client");

// set timeout for Hooks function, it allows to wait for long task
setDefaultTimeout(360 * 1000);

// initialize variables
let helpdesk_eventId = null;
let helpdesk_responseAPI = null;
let helpdesk_receipt = null;
let helpdesk_receiptError = null;
let helpdesk_listOfReceipts = [];


// After each Scenario
After(async function () {
    // remove event
    if (helpdesk_eventId != null) {
        await deleteDocumentFromBizEventsDatastore(helpdesk_eventId);
        await deleteDocumentFromReceiptsDatastoreByEventId(helpdesk_eventId);
        await deleteMultipleDocumentFromReceiptErrorDatastoreByEventId(helpdesk_eventId);
    }
    if(helpdesk_listOfReceipts.length > 0){
        for(let receipt of helpdesk_listOfReceipts){
            await deleteDocumentFromReceiptsDatastore(receipt.id);
            await deleteDocumentFromBizEventsDatastore(receipt.eventId);
            await deleteMultipleDocumentFromReceiptErrorDatastoreByEventId(receipt.eventId);
        }
    }
    if (helpdesk_receipt != null) {
        await deleteDocumentFromReceiptsDatastore(helpdesk_receipt.id);
        await deleteDocumentFromBizEventsDatastore(helpdesk_receipt.eventId);
        await deleteMultipleDocumentFromReceiptErrorDatastoreByEventId(helpdesk_receipt.eventId);
    }

    helpdesk_eventId = null;
    helpdesk_responseAPI = null;
    helpdesk_receipt = null;
    helpdesk_receiptError = null;
    helpdesk_listOfReceipts = [];
    bizEventIds = [];
});

//Given
Given('a receipt-error with bizEventId {string} and status {string} stored into receipt-error datastore', async function (id, status) {
    helpdesk_eventId = id;
    // prior cancellation to avoid dirty cases
    await deleteDocumentFromReceiptErrorDatastore(id);

    let receiptsStoreResponse = await createDocumentInReceiptErrorDatastore(id, status);
    assert.strictEqual(receiptsStoreResponse.statusCode, 201);
});

When("receiptToReviewed API is called with bizEventId {string}", async function (id) {
    helpdesk_responseAPI = await postReceiptToReviewed(id);
});

Then('the api response has a {int} Http status', function (expectedStatus) {
        assert.strictEqual(helpdesk_responseAPI.status, expectedStatus);
});

Then("the receipt-error with bizEventId {string} is recovered from datastore", async function (id) {
    let responseCosmos = await getDocumentFromReceiptsErrorDatastoreByBizEventId(id);
    assert.strictEqual(responseCosmos.resources.length > 0, true);
    helpdesk_receiptError = responseCosmos.resources[0];
});

Then("the receipt-error has not status {string}", async function (status) {
    assert.notStrictEqual(helpdesk_receiptError.status, status);
});

Given('a receipt with eventId {string} and status {string} stored into receipt datastore', async function (id, status) {
    helpdesk_eventId = id;
    // prior cancellation to avoid dirty cases
    await deleteDocumentFromReceiptsDatastore(id);

    let receiptsStoreResponse = await createDocumentInReceiptsDatastore(id, status);
    assert.strictEqual(receiptsStoreResponse.statusCode, 201);
});

Given('a biz event with id {string} and status {string} stored on biz-events datastore', async function (id, status) {
    helpdesk_eventId = id;
    // prior cancellation to avoid dirty cases
    await deleteDocumentFromBizEventsDatastore(helpdesk_eventId);

    let bizEventStoreResponse = await createDocumentInBizEventsDatastore(helpdesk_eventId, status);
    assert.strictEqual(bizEventStoreResponse.statusCode, 201);
});

When("recoverFailedReceipt API is called with eventId {string}", async function (id) {
    helpdesk_responseAPI = await postRecoverFailedReceipt(id);
});

Then("the receipt with eventId {string} is recovered from datastore", async function (id) {
    let responseCosmos = await getDocumentFromReceiptsDatastoreByEventIdOrdered(id);
    assert.strictEqual(responseCosmos.resources.length > 0, true);
    helpdesk_receipt = responseCosmos.resources[0];
});

Then('the receipt has not status {string}', function (targetStatus) {
    assert.notStrictEqual(helpdesk_receipt.status, targetStatus);
});

Given("a list of {int} receipts in status {string} stored into receipt datastore starting from eventId {string}", async function (numberOfReceipts, status, startingId) {
    helpdesk_listOfReceipts = [];
    for (let i = 0; i < numberOfReceipts; i++) {
        let nextEventId = startingId + i;
         // prior cancellation to avoid dirty cases
        await deleteDocumentFromReceiptsDatastoreByEventId(nextEventId);

        let receiptsStoreResponse = await createDocumentInReceiptsDatastore(nextEventId, status);
        assert.strictEqual(receiptsStoreResponse.statusCode, 201);

        helpdesk_listOfReceipts.push(receiptsStoreResponse.resource);
    }
});

Given("a list of {int} biz events in status {string} stored into biz-events datastore starting from eventId {string}", async function (numberOfEvents, status, startingId) {
    for (let i = 0; i < numberOfEvents; i++) {
        let nextEventId = startingId + i;
         // prior cancellation to avoid dirty cases
        await deleteDocumentFromBizEventsDatastore(nextEventId);

        let bizEventStoreResponse = await createDocumentInBizEventsDatastore(nextEventId, status);
        assert.strictEqual(bizEventStoreResponse.statusCode, 201);
    }
});

When("recoverFailedReceiptMassive API is called with status {string} as query param", async function (status) {
    helpdesk_responseAPI = await postRecoverFailedReceiptMassive(status);
});

Then("the list of receipt is recovered from datastore and no receipt in the list has status {string}", async function (status) {
    for (let recoveredReceipt of helpdesk_listOfReceipts) {
        let responseCosmos = await getDocumentFromReceiptsDatastoreByEventIdOrdered(recoveredReceipt.eventId);
        assert.strictEqual(responseCosmos.resources.length > 0, true);
        assert.notStrictEqual(responseCosmos.resources[0].status, status);
    }
});

When("recoverNotNotifiedReceipt API is called with eventId {string}", async function (id) {
    helpdesk_responseAPI = await postRecoverNotNotifiedReceipt(id);
});

When("recoverNotNotifiedReceiptMassive API is called with status {string} as query param", async function (status) {
    helpdesk_responseAPI = await postRecoverNotNotifiedReceiptMassive(status);
});