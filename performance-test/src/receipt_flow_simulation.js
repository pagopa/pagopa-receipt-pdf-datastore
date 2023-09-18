import { check } from 'k6';
import { SharedArray } from 'k6/data';

import { randomString, createEvent, SIM_TEST_CF } from './modules/common.js'
import { createDocument } from "./modules/datastore_client.js";

export let options = JSON.parse(open(__ENV.TEST_TYPE));

// read configuration
// note: SharedArray can currently only be constructed inside init code
// according to https://k6.io/docs/javascript-api/k6-data/sharedarray
const varsArray = new SharedArray('vars', function () {
	return JSON.parse(open(`./${__ENV.VARS}`)).environment;
});
// workaround to use shared array (only array should be used)
const vars = varsArray[0];
const bizEventCosmosDBURI = `${vars.bizEventCosmosDBURI}`;
const bizEventDatabaseID = `${vars.bizEventDatabaseID}`;
const bizEventContainerID = `${vars.bizEventContainerID}`;
const bizEventCosmosDBPrimaryKey = `${__ENV.BIZEVENT_COSMOS_DB_SUBSCRIPTION_KEY}`;

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

export default function () {
	// publish event
	let tag = { eventHubMethod: "SaveBizEvent" };
	const id = randomString(15, "abcdefghijklmnopqrstuvwxyz0123456789");
	let event = createEvent(id, SIM_TEST_CF);

	//	let r = createDocument(bizEventCosmosDBURI, bizEventDatabaseID, bizEventContainerID, bizEventCosmosDBPrimaryKey, event, id);
	let r = createDocument(bizEventCosmosDBURI, bizEventDatabaseID, bizEventContainerID, bizEventCosmosDBPrimaryKey, event, id);

	console.log("PublishEvent call, Status " + r.status);

	check(r, {
		'PublishEvent status is 201': (_response) => r.status === 201,
	}, tag);
}
