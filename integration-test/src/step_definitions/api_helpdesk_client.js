const axios = require("axios");

// --- INSTANCE CONFIGURATION ---
const helpdeskClient = axios.create({
    baseURL: process.env.HELPDESK_URL,
    headers: {
        'Ocp-Apim-Subscription-Key': process.env.HELPDESK_SUBKEY || ""
    }
});

if (process.env.canary) {
    helpdeskClient.defaults.headers.common['X-CANARY'] = 'canary';
}

// --- GENERIC HELPER FOR POST ---
/**
 * Performs a POST with an empty body and handles the return of the response or error.
 */
async function performPost(path) {
    try {
        return await helpdeskClient.post(path, {});
    } catch (error) {
        return error.response;
    }
}

// --- API FUNCTIONS ---

// Reviewed
async function postCartReceiptToReviewed(cartId) {
    const endpoint = (process.env.RECEIPT_TO_REVIEW_ENDPOINT || "cart-receipts-error/{cart-id}/reviewed")
        .replace("{cart-id}", cartId);
    return await performPost(endpoint);
}

async function postReceiptToReviewed(eventId) {
    const endpoint = (process.env.RECEIPT_TO_REVIEW_ENDPOINT || "receipts-error/{event-id}/reviewed")
        .replace("{event-id}", eventId);
    return await performPost(endpoint);
}

// Recover Failed
async function postRecoverFailedCartReceipt(cartId) {
    const endpoint = (process.env.RECOVER_FAILED_ENDPOINT || "cart-receipts/{cart-id}/recover-failed")
        .replace("{cart-id}", cartId);
    return await performPost(endpoint);
}

async function postRecoverFailedCartReceiptMassive(status) {
    const endpoint = (process.env.RECOVER_FAILED_MASSIVE_ENDPOINT || "cart-receipts/recover-failed?status={STATUS}")
        .replace("{STATUS}", status);
    return await performPost(endpoint);
}

async function postRecoverFailedReceipt(eventId) {
    const endpoint = (process.env.RECOVER_FAILED_ENDPOINT || "receipts/{event-id}/recover-failed")
        .replace("{event-id}", eventId);
    return await performPost(endpoint);
}

async function postRecoverFailedReceiptMassive(status) {
    const endpoint = (process.env.RECOVER_FAILED_MASSIVE_ENDPOINT || "receipts/recover-failed?status={STATUS}")
        .replace("{STATUS}", status);
    return await performPost(endpoint);
}

// Recover Not Notified
async function postRecoverNotNotifiedCartReceipt(cartId) {
    const endpoint = (process.env.RECOVER_NOT_NOTIFIED_ENDPOINT || "cart-receipts/{cart-id}/recover-not-notified")
        .replace("{cart-id}", cartId);
    return await performPost(endpoint);
}

async function postRecoverNotNotifiedCartReceiptMassive(status) {
    const endpoint = (process.env.RECOVER_NOT_NOTIFIED_MASSIVE_ENDPOINT || "cart-receipts/recover-not-notified?status={STATUS}")
        .replace("{STATUS}", status);
    return await performPost(endpoint);
}

async function postRecoverNotNotifiedReceipt(eventId) {
    const endpoint = (process.env.RECOVER_NOT_NOTIFIED_ENDPOINT || "receipts/{event-id}/recover-not-notified")
        .replace("{event-id}", eventId);
    return await performPost(endpoint);
}

async function postRecoverNotNotifiedReceiptMassive(status) {
    const endpoint = (process.env.RECOVER_NOT_NOTIFIED_MASSIVE_ENDPOINT || "receipts/recover-not-notified?status={STATUS}")
        .replace("{STATUS}", status);
    return await performPost(endpoint);
}

module.exports = {
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
};