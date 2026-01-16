const axios = require("axios");

const helpdesk_url = process.env.HELPDESK_URL;

axios.defaults.headers.common['Ocp-Apim-Subscription-Key'] = process.env.SUBKEY || ""; // for all requests
if (process.env.canary) {
	axios.defaults.headers.common['X-CANARY'] = 'canary' // for all requests
}

async function postReceiptToReviewed(eventId) {
	let endpoint = process.env.RECEIPT_TO_REVIEW_ENDPOINT || "receipts-error/{event-id}/reviewed";
	endpoint = endpoint.replace("{event-id}", eventId);

	return await axios.post(helpdesk_url + endpoint, {})
		.then(res => {
			return res;
		})
		.catch(error => {
			return error.response;
		});
}

async function postRecoverFailedReceipt(eventId) {
	let endpoint = process.env.RECOVER_FAILED_ENDPOINT || "receipts/{event-id}/recover-failed";
	endpoint = endpoint.replace("{event-id}", eventId);

	return await axios.post(helpdesk_url + endpoint, {})
		.then(res => {
			return res;
		})
		.catch(error => {
			return error.response;
		});
}

async function postRecoverFailedReceiptMassive(status) {
	let endpoint = process.env.RECOVER_FAILED_MASSIVE_ENDPOINT || "receipts/recover-failed?status={STATUS}";
	endpoint = endpoint.replace("{STATUS}", status);

	return await axios.post(helpdesk_url + endpoint, {})
		.then(res => {
			return res;
		})
		.catch(error => {
			return error.response;
		});
}

async function postRecoverNotNotifiedReceipt(eventId) {
	let endpoint = process.env.RECOVER_NOT_NOTIFIED_ENDPOINT || "receipts/{event-id}/recover-not-notified";
	endpoint = endpoint.replace("{event-id}", eventId);

	return await axios.post(helpdesk_url + endpoint, {})
		.then(res => {
			return res;
		})
		.catch(error => {
			return error.response;
		});
}

async function postRecoverNotNotifiedReceiptMassive(status) {
	let endpoint = process.env.RECOVER_NOT_NOTIFIED_MASSIVE_ENDPOINT || "receipts/recover-not-notified?status={STATUS}";
	endpoint = endpoint.replace("{STATUS}", status);

	return await axios.post(helpdesk_url + endpoint, {})
		.then(res => {
			return res;
		})
		.catch(error => {
			return error.response;
		});
}

module.exports = {
	postReceiptToReviewed,
	postRecoverFailedReceipt,
	postRecoverFailedReceiptMassive,
	postRecoverNotNotifiedReceipt,
	postRecoverNotNotifiedReceiptMassive,
}