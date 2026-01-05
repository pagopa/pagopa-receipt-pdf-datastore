const assert = require('assert');
const { Before, After, Given, When, Then, setDefaultTimeout } = require('@cucumber/cucumber');
let fs = require('fs');
const { sleep, makeId } = require("../../src/step_definitions/common");
const { 
    createDocumentInBizEventsDatastore, 
    createDocumentInBizEventsDatastoreIsCartEvent,
    createDocumentInBizEventsDatastoreWithIUVAndOrgCode,
    deleteDocumentFromBizEventsDatastore } = require("../../src/step_definitions/biz_events_datastore_client");
const {
    deleteDocumentFromReceiptsDatastore,
    createDocumentInReceiptsDatastore,
    deleteMultipleDocumentsFromReceiptsDatastoreByEventId,
    createDocumentInReceiptErrorDatastore,
    deleteDocumentFromReceiptErrorDatastore,
    getDocumentFromReceiptsErrorDatastoreByBizEventId,
    getDocumentFromReceiptsDatastoreByEventId,
    deleteMultipleDocumentFromReceiptErrorDatastoreByEventId,
    deleteDocumentFromReceiptMessageDatastore,
    createDocumentInReceiptIoMessageDatastore,
    createDocumentInCartDatastore,
    deleteDocumentFromCartDatastore,
    getDocumentFromCartDatastoreById
} = require("../../src/step_definitions/receipts_datastore_client");
const {
    getReceipt,
    getReceiptByOrganizationFiscalCodeAndIUV,
    getReceiptError,
    getReceiptMessage,
    getReceiptPdf,
    postReceiptToReviewed,
    postRecoverFailedReceipt,
    postRecoverFailedReceiptMassive,
    postRecoverNotNotifiedReceipt,
    postRecoverNotNotifiedReceiptMassive,
    postRegenerateReceiptPdf,
    postRecoverFailedCart,
    postRecoverFailedCartMassive
} = require("../../src/step_definitions/api_helpdesk_client");
const { uploadBlobFromLocalPath, deleteBlob, receiptPDFExist } = require("../../src/step_definitions/blob_storage_client");

// set timeout for Hooks function, it allows to wait for long task
setDefaultTimeout(360 * 1000);

// initialize variables
let eventId = null;
let messageId = null;
let responseAPI = null;
let receipt = null;
let receiptError = null;
let receiptMessage = null;
let receiptPdfFileName = null;
let listOfReceipts = [];
let bizEventIds = [];
let cart = null;
let cartList = [];

// Before each Scenario
Before({tags: '@clean-up-required'}, async function () {

})

// After each Scenario
After(async function () {
    // remove event
    if (eventId != null) {
        await deleteDocumentFromBizEventsDatastore(eventId);
        await deleteMultipleDocumentsFromReceiptsDatastoreByEventId(eventId);
        await deleteMultipleDocumentFromReceiptErrorDatastoreByEventId(eventId);
    }
    if (receiptPdfFileName != null) {
        await deleteBlob(receiptPdfFileName);
        if(fs.existsSync(receiptPdfFileName)){
            fs.unlinkSync(receiptPdfFileName);
        }
    }
    if(listOfReceipts.length > 0){
        for(let receipt of listOfReceipts){
            await deleteDocumentFromReceiptsDatastore(receipt.id);
            await deleteDocumentFromBizEventsDatastore(receipt.eventId);
        }
    }
    if (cart != null) {
        for (let id of cart.cartPaymentId) {
            await deleteDocumentFromBizEventsDatastore(id);
        }
        await deleteMultipleDocumentsFromReceiptsDatastoreByEventId(cart.id);
        await deleteDocumentFromCartDatastore(cart.id);
    }
    if(cartList.length > 0){
        for(let cart of cartList){
            for (let id of cart.cartPaymentId) {
                await deleteDocumentFromBizEventsDatastore(id);
            }
            await deleteMultipleDocumentsFromReceiptsDatastoreByEventId(cart.id);
            await deleteDocumentFromCartDatastore(cart.id);
        }
    }
    if (receipt != null) {
        await deleteDocumentFromReceiptsDatastore(receipt.id);
    }

    eventId = null;
    responseAPI = null;
    receipt = null;
    receiptError = null;
    receiptPdfFileName = null;
    listOfReceipts = [];
    bizEventIds = [];
    cart = null;
    cartList = [];
});

//Given
Given('a biz event with id {string} and status {string} stored on biz-events datastore', async function (id, status) {
    eventId = id;
    // prior cancellation to avoid dirty cases
    await deleteDocumentFromBizEventsDatastore(eventId);

    let bizEventStoreResponse = await createDocumentInBizEventsDatastore(eventId, status);
    assert.strictEqual(bizEventStoreResponse.statusCode, 201);
});

Given('a biz event with id {string} and status {string} and organizationFiscalCode {string} and IUV {string} stored on biz-events datastore', async function (id, status, orgCode, iuv) {
    eventId = id;
    // prior cancellation to avoid dirty cases
    await deleteDocumentFromBizEventsDatastore(eventId);

    let bizEventStoreResponse = await createDocumentInBizEventsDatastoreWithIUVAndOrgCode(id, status, orgCode, iuv);
    assert.strictEqual(bizEventStoreResponse.statusCode, 201);
  });

Given('a receipt with eventId {string} and status {string} stored into receipt datastore', async function (id, status) {
    eventId = id;
    // prior cancellation to avoid dirty cases
    await deleteDocumentFromReceiptsDatastore(id);

    let receiptsStoreResponse = await createDocumentInReceiptsDatastore(id, status);
    assert.strictEqual(receiptsStoreResponse.statusCode, 201);
});

Given('a receipt-error with bizEventId {string} and status {string} stored into receipt-error datastore', async function (id, status) {
    eventId = id;
    // prior cancellation to avoid dirty cases
    await deleteDocumentFromReceiptErrorDatastore(id);

    let receiptsStoreResponse = await createDocumentInReceiptErrorDatastore(id, status);
    assert.strictEqual(receiptsStoreResponse.statusCode, 201);
});

Given("a receipt pdf with filename {string} stored into blob storage", async function (fileName) {
    receiptPdfFileName = fileName;
    // prior cancellation to avoid dirty cases
    await deleteBlob(fileName);

    fs.writeFileSync(fileName, "", "binary");
    let blobStorageResponse = await uploadBlobFromLocalPath(fileName, fileName);
    assert.notStrictEqual(blobStorageResponse.status, 500);
});

Given("a list of {int} receipts in status {string} stored into receipt datastore starting from eventId {string}", async function (numberOfReceipts, status, startingId) {
    listOfReceipts = [];
    for (let i = 0; i < numberOfReceipts; i++) {
        let nextEventId = startingId + i;
         // prior cancellation to avoid dirty cases
        await deleteMultipleDocumentsFromReceiptsDatastoreByEventId(nextEventId);

        let receiptsStoreResponse = await createDocumentInReceiptsDatastore(nextEventId, status);
        assert.strictEqual(receiptsStoreResponse.statusCode, 201);

        listOfReceipts.push(receiptsStoreResponse.resource);
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

//When
When("getReceipt API is called with eventId {string}", async function (id) {
    responseAPI = await getReceipt(id);
    receipt = responseAPI.data;
});

When("getReceiptByOrganizationFiscalCodeAndIUV API is called with organizationFiscalCode {string} and IUV {string}", async function (orgCode, iuv) {
    responseAPI = await getReceiptByOrganizationFiscalCodeAndIUV(orgCode, iuv);
    receipt = responseAPI.data;
});

When("getReceiptError API is called with bizEventId {string}", async function (id) {
    responseAPI = await getReceiptError(id);
    receiptError = responseAPI.data;
});

When("getReceiptPdf API is called with filename {string}", async function (filename) {
    responseAPI = await getReceiptPdf(filename);
});

When("receiptToReviewed API is called with bizEventId {string}", async function (id) {
    responseAPI = await postReceiptToReviewed(id);
});

When("recoverFailedReceipt API is called with eventId {string}", async function (id) {
    responseAPI = await postRecoverFailedReceipt(id);
});

When("recoverFailedReceiptMassive API is called with status {string} as query param", async function (status) {
    responseAPI = await postRecoverFailedReceiptMassive(status);
});

When("recoverNotNotifiedReceipt API is called with eventId {string}", async function (id) {
    responseAPI = await postRecoverNotNotifiedReceipt(id);
});

When("recoverNotNotifiedReceiptMassive API is called with status {string} as query param", async function (status) {
    responseAPI = await postRecoverNotNotifiedReceiptMassive(status);
});

//Then
Then('the api response has a {int} Http status', function (expectedStatus) {
        assert.strictEqual(responseAPI.status, expectedStatus);
});

Then('the receipt has eventId {string}', function (targetId) {
    assert.strictEqual(receipt.eventId, targetId);
});

Then('the receipt has not status {string}', function (targetStatus) {
    assert.notStrictEqual(receipt.status, targetStatus);
});

Then("the receipt-error has bizEventId {string}", async function (id) {
    assert.strictEqual(receiptError.bizEventId, id);
});

Then("the receipt-error payload has bizEvent decrypted with eventId {string}", async function (id) {
    let messagePayload = JSON.parse(receiptError.messagePayload);
    assert.strictEqual(messagePayload.id, id);
});

Then("the receipt-error has not status {string}", async function (status) {
    assert.notStrictEqual(receiptError.status, status);
});

Then("the receipt-error with bizEventId {string} is recovered from datastore", async function (id) {
    let responseCosmos = await getDocumentFromReceiptsErrorDatastoreByBizEventId(id);
    assert.strictEqual(responseCosmos.resources.length > 0, true);
    receiptError = responseCosmos.resources[0];
});

Then("the receipt with eventId {string} is recovered from datastore", async function (id) {
    let responseCosmos = await getDocumentFromReceiptsDatastoreByEventId(id);
    assert.strictEqual(responseCosmos.resources.length > 0, true);
    receipt = responseCosmos.resources[0];
});

Then("the list of receipt is recovered from datastore and no receipt in the list has status {string}", async function (status) {
    for (let recoveredReceipt of listOfReceipts) {
        let responseCosmos = await getDocumentFromReceiptsDatastoreByEventId(recoveredReceipt.eventId);
        assert.strictEqual(responseCosmos.resources.length > 0, true);
        assert.notStrictEqual(responseCosmos.resources[0].status, status);
    }
});

Then('the receipt has attachment metadata', function () {
    assert.strictEqual(receipt.mdAttach != undefined && receipt.mdAttach != null && receipt.mdAttach.name != "", true);
    receiptPdfFileName = receipt.mdAttach.name;
});

Then('the PDF is present on blob storage', async function () {
    let blobExist = await receiptPDFExist(receiptPdfFileName);
    assert.strictEqual(blobExist, true);
  });

Then("wait {int} ms", async function (milliSec) {
    sleep(milliSec)
});

Given('a receipt-io-message with bizEventId {string} and messageId {string} stored into receipt-io-message datastore', async function (eventId, messageId) {
    messageId = messageId;
    // prior cancellation to avoid dirty cases
    await deleteDocumentFromReceiptMessageDatastore(messageId);

    let receiptsMessageStoreResponse = await createDocumentInReceiptIoMessageDatastore(eventId, messageId);
    assert.strictEqual(receiptsMessageStoreResponse.statusCode, 201);
});

When("getReceiptMessage API is called with messageId {string}", async function (id) {
    responseAPI = await getReceiptMessage(id);
    receiptMessage = responseAPI.data;
});

Then("the receipt-message has eventId {string}", async function (id) {
    assert.strictEqual(receiptMessage.eventId, id);
});

Then("the receipt-message has messageId {string}", async function (id) {
    assert.strictEqual(receiptMessage.messageId, id);
});


Given('a biz event with transactionId {string} and status {string} stored on biz-events datastore', async function (transactionId, status) {
    let id = transactionId + makeId(5);
    
    let bizEventStoreResponse = await createDocumentInBizEventsDatastoreIsCartEvent(id, transactionId, status, "2");
    assert.strictEqual(bizEventStoreResponse.statusCode, 201);
    bizEventIds.push(id);
});

Given('a cart with id {string} and status {string} stored into cart datastore', async function (id, status) {
    // prior cancellation to avoid dirty cases
    await deleteDocumentFromCartDatastore(id);

    let cartStoreResponse = await createDocumentInCartDatastore(id, bizEventIds, status);
    assert.strictEqual(cartStoreResponse.statusCode, 201);
    cart = cartStoreResponse.resource;
});

When("recoverFailedCart API is called with cartId {string}", async function (id) {
    responseAPI = await postRecoverFailedCart(id);
});

Then("the cart with id {string} is retrieved from datastore", async function (id) {
    let responseCosmos = await getDocumentFromCartDatastoreById(id);
    assert.strictEqual(responseCosmos.resources.length > 0, true);
    cart = responseCosmos.resources[0];
});

Then('the cart has not status {string}', function (targetStatus) {
    assert.notStrictEqual(cart.status, targetStatus);
});

Given("a list of {int} carts in status {string} stored into cart datastore starting from id {string}", async function (numberOfCarts, status, startingId) {
    cartList = [];
    for (let i = 0; i < numberOfCarts; i++) {
        let nextTransactionId = startingId + i;

        let id = nextTransactionId + makeId(5);
        let id2 = nextTransactionId + makeId(5);
        let cartStoreResponse = await createDocumentInCartDatastore(nextTransactionId, [id, id2], status);
        assert.strictEqual(cartStoreResponse.statusCode, 201);
        cartList.push(cartStoreResponse.resource);
    }
});

Given("a list of {int} biz events in status {string} stored into biz-events datastore", async function (numberOfEvents, status) {
    assert.strictEqual(numberOfEvents / 2, cartList.length);
    for (let cart of cartList) {
        let transactionId = cart.id;
        for (let bizEventId of cart.cartPaymentId) {
            let bizEventStoreResponse = await createDocumentInBizEventsDatastoreIsCartEvent(bizEventId, transactionId, status, "2");
            assert.strictEqual(bizEventStoreResponse.statusCode, 201);
        }
    }
});

When("recoverFailedCartMassive API is called with status {string} as query param", async function (status) {
    responseAPI = await postRecoverFailedCartMassive(status);
});

Then('the list of cart is retrieved from datastore and no cart in the list has status {string}', async function (status) {
    for (let cart of cartList) {
        let responseCosmos = await getDocumentFromCartDatastoreById(cart.id);
        assert.strictEqual(responseCosmos.resources.length > 0, true);
        assert.notStrictEqual(responseCosmos.resources[0].status, status);
    }
})

Then('the list of receipt is retrieved from datastore and no receipt in the list has status {string}', async function (status) {
    listOfReceipts = [];
    for (let cart of cartList) {
        let responseCosmos = await getDocumentFromReceiptsDatastoreByEventId(cart.id);
        assert.strictEqual(responseCosmos.resources.length > 0, true);
        assert.notStrictEqual(responseCosmos.resources[0].status, status);
        listOfReceipts.push(responseCosmos.resources[0]);
    }
})
