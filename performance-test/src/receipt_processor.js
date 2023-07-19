import { sleep, check } from 'k6';
import { SharedArray } from 'k6/data';

import { randomString, createEvent } from './modules/common.js'
import { createDocument, deleteDocument, getDocumentByEventId } from "./modules/datastore_client.js";

export let options = JSON.parse(open(__ENV.TEST_TYPE));

// read configuration
// note: SharedArray can currently only be constructed inside init code
// according to https://k6.io/docs/javascript-api/k6-data/sharedarray
const varsArray = new SharedArray('vars', function() {
	return JSON.parse(open(`./${__ENV.VARS}`)).environment;
});
// workaround to use shared array (only array should be used)
const vars = varsArray[0];
const cosmosServiceURI = `${vars.cosmosServiceURI}`;
const bizEventCosmosDBURI = `${vars.bizEventCosmosDBURI}`;
const bizEventDatabaseID = `${vars.bizEventDatabaseID}`;
const bizEventContainerID = `${vars.bizEventContainerID}`;
const bizEventCosmosDBPrimaryKey = `${__ENV.BIZEVENT_COSMOS_DB_SUBSCRIPTION_KEY}`;
const receiptCosmosDBURI = `${vars.receiptCosmosDBURI}`;
const receiptDatabaseID = `${vars.receiptDatabaseID}`;
const receiptContainerID = `${vars.receiptContainerID}`;
const receiptCosmosDBPrimaryKey = `${__ENV.RECEIPT_COSMOS_DB_SUBSCRIPTION_KEY}`;
// boundary time (s) to process event: activate trigger, process function, upload event to datastore
const processTime = `${vars.processTime}`;

export function setup() {
	// 2. setup code (once)
	// The setup code runs, setting up the test environment (optional) and generating data
	// used to reuse code for the same VU

	// todo

	// precondition is moved to default fn because in this stage
	// __VU is always 0 and cannot be used to create env properly
}

// teardown the test data
export function teardown(data) {
	// todo
}

function postcondition(eventId) {
	// verify that published event have been stored properly in the datastore
	let tag = { datastoreMethod: "GetDocumentByEventId" };
	let r = getDocumentByEventId(cosmosServiceURI, receiptCosmosDBURI, receiptDatabaseID, receiptContainerID, receiptCosmosDBPrimaryKey, eventId);

	console.log("GetDocumentByEventId call, Status " + r.status);

	let response = r.json();
	let documents = response.documents;

	if(response && response.status === 200 && documents && documents.length > 0) {
		let document = documents[0];

		check(r, {
			"Assert published receipt is in the datastore and with status GENERATED": (_r) => documents.length === 1 && document.status === "GENERATED",
		}, tag);

		console.log("Document with status " + r.status);

		let receiptId = document.id;

		deleteDocument(cosmosServiceURI, bizEventCosmosDBURI, bizEventDatabaseID, bizEventContainerID, bizEventCosmosDBPrimaryKey, eventId, eventId);
		deleteDocument(cosmosServiceURI, receiptCosmosDBURI, receiptDatabaseID, receiptContainerID, receiptCosmosDBPrimaryKey, receiptId, eventId);
	}

}

export default function() {
	// publish event
	let tag = { eventHubMethod: "SaveBizEvent" };
	const id = randomString(15, "abcdefghijklmnopqrstuvwxyz0123456789");
	let event = createEvent(id);

	//	let r = createDocument(bizEventCosmosDBURI, bizEventDatabaseID, bizEventContainerID, bizEventCosmosDBPrimaryKey, event, id);
	let r = createDocument(cosmosServiceURI, bizEventCosmosDBURI, bizEventDatabaseID, bizEventContainerID, bizEventCosmosDBPrimaryKey, event);
	let response = r.json();
	console.log("PublishEvent call, Status " + response.status);

	check(r, {
		'PublishEvent status is 201': (_response) => response.status === 201,
	}, tag);

	// if the event is published wait and check if it was correctly processed and stored in the datastore
	if (response.status === 201) {
		sleep(processTime);
		postcondition(id);
	}
}
