const assert = require('assert');
const { After, Given, When, Then, setDefaultTimeout } = require('@cucumber/cucumber');
const { sleep, recoverFailedEvent } = require("./common");
const { createDocumentInBizEventsDatastore, deleteDocumentFromBizEventsDatastore } = require("./biz_events_datastore_client");
const { getDocumentByIdFromReceiptsDatastore, deleteDocumentFromReceiptsDatastoreByEventId, deleteDocumentFromReceiptsDatastore, deleteDocumentFromCartDatastore, createDocumentInCartDatastore, getCartDocumentByIdFromReceiptsDatastore } = require("./receipts_datastore_client");

// set timeout for Hooks function, it allows to wait for long task
setDefaultTimeout(360 * 1000);

// initialize variables
this.eventId = null;
this.responseToCheck = null;
this.response = null;
this.receiptId = null;
this.event = null;
this.cartId = null;
this.listOfBizEventsIds = null;

// After each Scenario
After(async function () {
    // remove event
    if (this.eventId != null) {
        await deleteDocumentFromBizEventsDatastore(this.eventId);
    }
    if (this.receiptId != null) {
        await deleteDocumentFromReceiptsDatastore(this.receiptId);
    }
    if(this.cartId != null) {
        await deleteDocumentFromCartDatastore(this.cartId);
    }
    if(this.listOfBizEventsIds?.length > 0) {
        for(bizEvent of this.listOfBizEventsIds){
            await deleteDocumentFromBizEventsDatastore(bizEvent);
        }
    }
    this.eventId = null;
    this.responseToCheck = null;
    this.receiptId = null;
    this.event = null;
    this.listOfBizEventsIds = null;
    this.cartId = null;
});

Given('a random biz event with id {string} stored on biz-events datastore with status DONE', async function (id) {
    this.eventId = id;
    // prior cancellation to avoid dirty cases
    await deleteDocumentFromBizEventsDatastore(this.eventId);
    await deleteDocumentFromReceiptsDatastoreByEventId(this.eventId);

    let bizEventStoreResponse = await createDocumentInBizEventsDatastore(this.eventId);
    assert.strictEqual(bizEventStoreResponse.statusCode, 201);
});

Given('a list of {int} bizEvents starting with id {string} and transactionId {string} stored on biz-events datastore with status DONE', async function (numberOfEvents, id, transactionId) {
      this.eventId = transactionId;
      this.listOfBizEventsIds = [];

      for(let i = 0; i < numberOfEvents; i++) {
        let finalId = id+i;

        await deleteDocumentFromBizEventsDatastore(finalId);
        await deleteDocumentFromReceiptsDatastoreByEventId(finalId);

        let bizEventStoreResponse = await createDocumentInBizEventsDatastore(finalId, transactionId, `${numberOfEvents}`);
        assert.strictEqual(bizEventStoreResponse.statusCode, 201);

        this.listOfBizEventsIds.push(finalId);
      }
});

Given('a cart event with id {string} containing the ids the bizEvents', async function (id) {
    this.cartId = id;
    // prior cancellation to avoid dirty cases
    await deleteDocumentFromCartDatastore(id);

    let cartResponse = await createDocumentInCartDatastore(id, this.listOfBizEventsIds);
    assert.strictEqual(cartResponse.statusCode, 201);
});

When('biz event has been properly stored into receipt datastore after {int} ms with eventId {string}', async function (time, eventId) {
    // boundary time spent by azure function to process event
    await sleep(time);
    this.responseToCheck = await getDocumentByIdFromReceiptsDatastore(eventId);
});

When('receipt has been properly stored into receipt datastore after {int} ms with eventId {string}', async function (time, id) {
    // boundary time spent by azure function to process event
    await sleep(time);
    this.responseToCheck = await getDocumentByIdFromReceiptsDatastore(id);
});

When('cart event has been properly stored into receipt datastore after {int} ms with id {string}', async function (time, id) {
    // boundary time spent by azure function to process event
    await sleep(time);
    this.responseToCheck = await getCartDocumentByIdFromReceiptsDatastore(id);
});

Then('the receipts datastore returns the receipt', async function () {
    assert.notStrictEqual(this.responseToCheck.resources.length, 0);
    this.receiptId = this.responseToCheck.resources[0].id;
    assert.strictEqual(this.responseToCheck.resources.length, 1);
});

Then('the receipts datastore return the cart event', async function () {
    assert.notStrictEqual(this.responseToCheck.resources.length, 0);
    this.cartId = this.responseToCheck.resources[0].id;
    assert.strictEqual(this.responseToCheck.resources.length, 1);
});

Then("the cart event has status {string}", function(status) {
    assert.strictEqual(this.responseToCheck.resources[0].status, status);
});

Then('the receipt has eventId {string}', function (targetId) {
    assert.strictEqual(this.responseToCheck.resources[0].eventId, targetId);
});

Then('the receipt has not the status {string}', function (targetStatus) {
    assert.notStrictEqual(this.responseToCheck.resources[0].status, targetStatus);
});

Then("the receipt has not a datastore reason error message", function(){
    let receiptResponse = this.responseToCheck.resources[0];
    if(receiptResponse?.reasonErr.message){
        let booleanResponseErr = receiptResponse.reasonErr.message.includes("BizEventToReceiptService") || !receiptResponse?.eventData?.amount;

        assert.strictEqual(booleanResponseErr, false);
    }
});



