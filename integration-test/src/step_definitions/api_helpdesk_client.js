const axios = require("axios");

const helpdesk_url = process.env.HELPDESK_URL;

axios.defaults.headers.common['Ocp-Apim-Subscription-Key'] = process.env.SUBKEY || ""; // for all requests
if (process.env.canary) {
	axios.defaults.headers.common['X-CANARY'] = 'canary' // for all requests
}

async function getReceipt(id) {
	let endpoint = process.env.GET_RECEIPT_ENDPOINT || "receipts/{event-id}";
	endpoint = endpoint.replace("{event-id}", id);
	console.debug("GET RECEIPT", helpdesk_url + endpoint)
	return await axios.get(helpdesk_url + endpoint)
		.then(res => {
			return res;
		})
		.catch(error => {
			return error.response;
		});
}

async function getReceiptMessage(id) {
	let endpoint = process.env.GET_RECEIPT_ENDPOINT || "receipts/io-message/{message-id}";
	endpoint = endpoint.replace("{message-id}", id);

	return await axios.get(helpdesk_url + endpoint)
		.then(res => {
			return res;
		})
		.catch(error => {
			return error.response;
		});
}

async function getReceiptByOrganizationFiscalCodeAndIUV(orgCode, iuv) {
	let endpoint = process.env.GET_RECEIPT_BY_ORGCODE_AND_IUV_ENDPOINT || "receipts/organizations/{organization-fiscal-code}/iuvs/{iuv}";
	endpoint = endpoint.replace("{organization-fiscal-code}", orgCode);
	endpoint = endpoint.replace("{iuv}", iuv);

	return await axios.get(helpdesk_url + endpoint)
		.then(res => {
			return res;
		})
		.catch(error => {
			return error.response;
		});
}

async function getReceiptError(id) {
	let endpoint = process.env.GET_RECEIPT_ERROR_ENDPOINT || "errors-toreview/{bizvent-id}";
	endpoint = endpoint.replace("{bizvent-id}", id);

	return await axios.get(helpdesk_url + endpoint)
		.then(res => {
			return res;
		})
		.catch(error => {
			return error.response;
		});
}

async function getReceiptPdf(fileName) {
	let endpoint = process.env.GET_RECEIPT_PDF_ENDPOINT || "pdf-receipts/{file-name}";
	endpoint = endpoint.replace("{file-name}", fileName);

	return await axios.get(helpdesk_url + endpoint)
		.then(res => {
			return res;
		})
		.catch(error => {
			return error.response;
		});
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

async function postRegenerateReceiptPdf(eventId) {
	let endpoint = process.env.REGENERATE_RECEIPT_PDF_ENDPOINT || "receipts/{bizevent-id}/regenerate-receipt-pdf";
	endpoint = endpoint.replace("{bizevent-id}", eventId);
	console.debug("POST RECOVER MASSIVE FAILED RECEIPT", helpdesk_url + endpoint);
	return await axios.post(helpdesk_url + endpoint, {})
		.then(res => {
			return res;
		})
		.catch(error => {
			return error.response;
		});
}

async function postRecoverFailedCart(cartId) {
	let endpoint = process.env.RECOVER_FAILED_CART_ENDPOINT || "carts/{cart-id}/recover-failed";
	endpoint = endpoint.replace("{cart-id}", cartId);

	return await axios.post(helpdesk_url + endpoint, {})
		.then(res => {
			return res;
		})
		.catch(error => {
			return error.response;
		});
}

async function postRecoverFailedCartMassive(status) {
	let endpoint = process.env.RECOVER_FAILED_CART_MASSIVE_ENDPOINT || "carts/recover-failed?status={STATUS}";
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
}