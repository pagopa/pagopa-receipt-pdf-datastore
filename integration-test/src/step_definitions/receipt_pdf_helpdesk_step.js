const assert = require('assert');
const { After, Given, When, Then, setDefaultTimeout } = require('@cucumber/cucumber');
const {
    createDocumentInBizEventsDatastore,
    deleteDocumentFromBizEventsDatastore,
    deleteDocumentByCartIdFromBizEventsDatastore
 } = require("../../src/step_definitions/biz_events_datastore_client");
const {
    deleteDocumentFromReceiptsDatastore,
    createDocumentInReceiptsDatastore,
    createDocumentInReceiptErrorDatastore,
    deleteDocumentFromReceiptErrorDatastore,
    getDocumentFromReceiptsErrorDatastoreByBizEventId,
    getDocumentFromReceiptsDatastoreByEventIdOrdered,
    deleteDocumentFromReceiptsDatastoreByEventId,
    deleteDocumentFromCartReceiptErrorDatastore,
    createDocumentInCartReceiptErrorDatastore,
    getDocumentFromCartReceiptsErrorDatastoreById,
    createDocumentInCartReceiptsDatastore,
    deleteDocumentFromCartDatastore,
    getCartDocumentByIdFromReceiptsDatastore,
    deleteMultipleDocumentFromReceiptErrorDatastoreByEventId,
    deleteAllHelpdeskDocumentFromErrorCartDatastore
} = require("../../src/step_definitions/receipts_datastore_client");
const {
    postCartReceiptToReviewed,
    postReceiptToReviewed,
    postRecoverFailedCartReceipt,
    postRecoverFailedCartReceiptMassive,
    postRecoverFailedReceipt,
    postRecoverFailedReceiptMassive,
    postRecoverNotNotifiedCartReceipt,
    postRecoverNotNotifiedCartReceiptMassive,
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
let helpdesk_cartId = null;
let helpdesk_cartReceiptError = null;
let helpdesk_cart = null;
let helpdesk_listOfCarts = [];


// After each Scenario
After(async function () {
    // remove event
     if (helpdesk_eventId != null) {
         await deleteDocumentFromBizEventsDatastore(helpdesk_eventId);
         await deleteDocumentFromReceiptsDatastoreByEventId(helpdesk_eventId);
         await deleteMultipleDocumentFromReceiptErrorDatastoreByEventId(helpdesk_eventId);
     }
     if (helpdesk_listOfReceipts.length > 0) {
         for (let receipt of helpdesk_listOfReceipts) {
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
     if (helpdesk_receiptError != null) {
         await deleteDocumentFromReceiptErrorDatastore(helpdesk_receiptError.id);
     }
     if (helpdesk_cartReceiptError != null) {
         await deleteDocumentFromCartReceiptErrorDatastore(helpdesk_cartReceiptError.id);
     }
     if (helpdesk_cartId != null) {
         await deleteDocumentFromCartDatastore(helpdesk_cartId);
         await deleteDocumentByCartIdFromBizEventsDatastore(helpdesk_cartId);
     }
     if (helpdesk_cart != null) {
         await deleteDocumentFromCartDatastore(helpdesk_cart.id);
         for (let cart of helpdesk_cart.payload.cart) {
             await deleteDocumentFromBizEventsDatastore(cart.bizEventId);
         }
     }
     if (helpdesk_listOfCarts.length > 0) {
         for (let cart of helpdesk_listOfCarts) {
             await deleteDocumentFromCartDatastore(cart.id);
             await deleteDocumentByCartIdFromBizEventsDatastore(cart.id);
         }
     }
     deleteAllHelpdeskDocumentFromErrorCartDatastore();

    helpdesk_eventId = null;
    helpdesk_responseAPI = null;
    helpdesk_receipt = null;
    helpdesk_receiptError = null;
    helpdesk_listOfReceipts = [];
    bizEventIds = [];
    helpdesk_cartId = null;
    helpdesk_cartReceiptError = null;
    helpdesk_cart = null;
    helpdesk_listOfCarts = [];
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
        let nextEventId = `${startingId}-${i}`;
        // prior cancellation to avoid dirty cases
        await deleteDocumentFromReceiptsDatastoreByEventId(nextEventId);

        let receiptsStoreResponse = await createDocumentInReceiptsDatastore(nextEventId, status);
        assert.strictEqual(receiptsStoreResponse.statusCode, 201);

        helpdesk_listOfReceipts.push(receiptsStoreResponse.resource);
    }
});

Given("a list of {int} biz events in status {string} stored into biz-events datastore starting from eventId {string}", async function (numberOfEvents, status, startingId) {
    for (let i = 0; i < numberOfEvents; i++) {
        let nextEventId = `${startingId}-${i}`;
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


Given('a cart-receipt-error with cartId {string} and status {string} stored into cart-receipt-error datastore', async (id, status) => {
    helpdesk_cartId = id;
    // prior cancellation to avoid dirty cases
    await deleteDocumentFromCartReceiptErrorDatastore(id);

    let receiptsStoreResponse = await createDocumentInCartReceiptErrorDatastore(id, status);
    assert.strictEqual(receiptsStoreResponse.statusCode, 201);
})

When('cartReceiptToReviewed API is called with cartId {string}', async (id) => {
    helpdesk_responseAPI = await postCartReceiptToReviewed(id);
})

Then('the cart-receipt-error with cartId {string} is recovered from datastore', async (id) => {
    let responseCosmos = await getDocumentFromCartReceiptsErrorDatastoreById(id);
    assert.strictEqual(responseCosmos.resources.length > 0, true);
    helpdesk_cartReceiptError = responseCosmos.resources[0];
})

Then('the cart-receipt-error has not status {string}', async (status) => {
    assert.notStrictEqual(helpdesk_cartReceiptError.status, status);
})

Given('a cart receipt with cartId {string} and status {string} stored into receipt datastore', async (id, status) => {
    helpdesk_cartId = id;
    // prior cancellation to avoid dirty cases
    await deleteDocumentFromCartDatastore(id);

    let receiptsStoreResponse = await createDocumentInCartReceiptsDatastore(id, status);
    assert.strictEqual(receiptsStoreResponse.statusCode, 201);
})

Given('a list of {int} biz events of a cart stored into biz-events datastore starting from eventId {string}', async (numberOfEvents, startingId) => {
      for (let i = 0; i < numberOfEvents; i++) {
        let nextEventId = `${startingId}-${i}`;
        // prior cancellation to avoid dirty cases
        await deleteDocumentFromBizEventsDatastore(nextEventId);

        let bizEventStoreResponse = await createDocumentInBizEventsDatastore(nextEventId, startingId, `${numberOfEvents}`);
        assert.strictEqual(bizEventStoreResponse.statusCode, 201);
    }
})

When('recoverFailedCartReceipt API is called with cartId {string}', async (id) => {
    helpdesk_responseAPI = await postRecoverFailedCartReceipt(id);
})

Then('the cart receipt with cartId {string} is recovered from datastore', async (id) => {
    let responseCosmos = await getCartDocumentByIdFromReceiptsDatastore(id);
    assert.strictEqual(responseCosmos.resources.length > 0, true);
    helpdesk_cart = responseCosmos.resources[0];
})

Then('the cart receipt has not status {string}', (targetStatus) => {
    assert.notStrictEqual(helpdesk_cart.status, targetStatus);
})

Given('a list of {int} cart receipts in status {string} stored into cart receipt datastore starting from cartId {string}', async function (numberOfCarts, status, startingId) {
    helpdesk_listOfCarts = [];
    for (let i = 0; i < numberOfCarts; i++) {
        let nextEventId = `${startingId}-${i}`;
        // prior cancellation to avoid dirty cases
        await deleteDocumentFromCartDatastore(nextEventId);

        let receiptsStoreResponse = await createDocumentInCartReceiptsDatastore(nextEventId, status);
        assert.strictEqual(receiptsStoreResponse.statusCode, 201);

        helpdesk_listOfCarts.push(receiptsStoreResponse.resource);
    }
})

Given('a list of {int} biz events for each cart is stored into biz-events datastore', async function (numberOfEvents) {
    for (let cart of helpdesk_listOfCarts) {
        for (let i = 0; i < numberOfEvents; i++) {
            let nextEventId = `${cart.cartId}-${i}`;
            // prior cancellation to avoid dirty cases
            await deleteDocumentFromBizEventsDatastore(nextEventId);

            let bizEventStoreResponse = await createDocumentInBizEventsDatastore(nextEventId, cart.cartId, `${numberOfEvents}`);
            assert.strictEqual(bizEventStoreResponse.statusCode, 201);
        }
    }
});
When('recoverFailedCartReceiptMassive API is called with status {string} as query param', async (status) => {
    helpdesk_responseAPI = await postRecoverFailedCartReceiptMassive(status);
})

Then('the list of cart receipt is recovered from datastore and no cart receipt in the list has status {string}', async (status) => {
      for (let recoveredCart of helpdesk_listOfCarts) {
        let responseCosmos = await getCartDocumentByIdFromReceiptsDatastore(recoveredCart.cartId);
        assert.strictEqual(responseCosmos.resources.length > 0, true);
        assert.notStrictEqual(responseCosmos.resources[0].status, status);
    }
})

When('recoverNotNotifiedCartReceipt API is called with cartId {string}', async (id) => {
  helpdesk_responseAPI = await postRecoverNotNotifiedCartReceipt(id);
})

When('recoverNotNotifiedCartReceiptMassive API is called with status {string} as query param', async (status) => {
   helpdesk_responseAPI = await postRecoverNotNotifiedCartReceiptMassive(status);
})
