function sleep(ms) {
	return new Promise(resolve => setTimeout(resolve, ms));
}

function createEvent(id) {
	let json_event = {
		"id": id,
		"version": "2",
		"complete": "false",
		"receiptId": "0095ff2bafec4bc0a719c9bf003aee4a",
		"missingInfo": [
			"idPaymentManager",
			"psp.pspPartitaIVA",
			"paymentInfo.primaryCiIncurredFee",
			"paymentInfo.idBundle",
			"paymentInfo.idCiBundle",
			"paymentInfo.metadata"
		],
		"debtorPosition": {
			"modelType": "2",
			"noticeNumber": "302115802768026801",
			"iuv": "02115802768026801"
		},
		"creditor": {
			"idPA": "66666666666",
			"idBrokerPA": "66666666666",
			"idStation": "66666666666_01",
			"companyName": "PA paolo",
			"officeName": "office PA"
		},
		"psp": {
			"idPsp": "POSTE3",
			"idBrokerPsp": "BANCOPOSTA",
			"idChannel": "POSTE3",
			"psp": "Poste Italiane",
			"pspFiscalCode": "CFPOSTE3",
			"channelDescription": "app"
		},
		"debtor": {
			"fullName": "John Doe",
			"entityUniqueIdentifierType": "F",
			"entityUniqueIdentifierValue": "JHNDOE00A01F205N",
			"streetName": "street",
			"civicNumber": "12",
			"postalCode": "89020",
			"city": "city",
			"stateProvinceRegion": "MI",
			"country": "IT",
			"eMail": "john.doe@test.it"
		},
		"payer": {
			"fullName": "John Doe",
			"entityUniqueIdentifierType": "F",
			"entityUniqueIdentifierValue": "JHNDOE00A01F205S",
			"streetName": "street",
			"civicNumber": "12",
			"postalCode": "89020",
			"city": "city",
			"stateProvinceRegion": "MI",
			"country": "IT",
			"eMail": "john.doe@test.it"
		},
		"paymentInfo": {
			"paymentDateTime": "2022-12-13T01:52:02.926587",
			"applicationDate": "2021-10-01",
			"transferDate": "2021-10-02",
			"dueDate": "2021-07-31",
			"paymentToken": "0095ff2bafec4bc0a719c9bf003aee4a",
			"amount": "70.0",
			"fee": "2.0",
			"totalNotice": "1",
			"paymentMethod": "creditCard",
			"touchpoint": "app",
			"remittanceInformation": "TARI 2021",
			"description": "TARI 2021"
		},
		"transferList": [
			{
				"idTransfer": "1",
				"fiscalCodePA": "77777777777",
				"companyName": "Pa Salvo",
				"amount": "70.0",
				"transferCategory": "0101101IM",
				"remittanceInformation": "TARI Comune EC_TE"
			}
		],
		"eventStatus": "DONE",
		"eventRetryEnrichmentCount": 0,
		"_rid": "sMJGAMl3HZnqAQAAAAAAAA==",
		"_self": "dbs/sMJGAA==/colls/sMJGAMl3HZk=/docs/sMJGAMl3HZnqAQAAAAAAAA==/",
		"_etag": "\"2400e1e4-0000-0d00-0000-6397ccb60000\"",
		"_attachments": "attachments/",
		"_ts": 1670892726
	}
	return json_event
}

function createEventForQueue(id) {
	return createEventForPoisonQueue(id, false);
}

function createEventForPoisonQueue(id, attemptedPoisonRetry) {
	let json_event = {
		"id": id,
		"version": "2",
		"complete": "false",
		"receiptId": "0095ff2bafec4bc0a719c9bf003aee4a",
		"attemptedPoisonRetry": attemptedPoisonRetry,
		"missingInfo": [
			"idPaymentManager",
			"psp.pspPartitaIVA",
			"paymentInfo.primaryCiIncurredFee",
			"paymentInfo.idBundle",
			"paymentInfo.idCiBundle",
			"paymentInfo.metadata"
		],
		"debtorPosition": {
			"modelType": "2",
			"noticeNumber": "302115802768026801",
			"iuv": "02115802768026801"
		},
		"creditor": {
			"idPA": "66666666666",
			"idBrokerPA": "66666666666",
			"idStation": "66666666666_01",
			"companyName": "PA paolo",
			"officeName": "office PA"
		},
		"psp": {
			"idPsp": "POSTE3",
			"idBrokerPsp": "BANCOPOSTA",
			"idChannel": "POSTE3",
			"psp": "Poste Italiane",
			"pspFiscalCode": "CFPOSTE3",
			"channelDescription": "app"
		},
		"debtor": {
			"fullName": "John Doe",
			"entityUniqueIdentifierType": "F",
			"entityUniqueIdentifierValue": "JHNDOE00A01F205N",
			"streetName": "street",
			"civicNumber": "12",
			"postalCode": "89020",
			"city": "city",
			"stateProvinceRegion": "MI",
			"country": "IT",
			"eMail": "john.doe@test.it"
		},
		"payer": {
			"fullName": "John Doe",
			"entityUniqueIdentifierType": "F",
			"entityUniqueIdentifierValue": "JHNDOE00A01F205S",
			"streetName": "street",
			"civicNumber": "12",
			"postalCode": "89020",
			"city": "city",
			"stateProvinceRegion": "MI",
			"country": "IT",
			"eMail": "john.doe@test.it"
		},
		"paymentInfo": {
			"paymentDateTime": "2022-12-13T01:52:02.926587",
			"applicationDate": "2021-10-01",
			"transferDate": "2021-10-02",
			"dueDate": "2021-07-31",
			"paymentToken": "0095ff2bafec4bc0a719c9bf003aee4a",
			"amount": "70.0",
			"fee": "2.0",
			"totalNotice": "1",
			"paymentMethod": "creditCard",
			"touchpoint": "app",
			"remittanceInformation": "TARI 2021",
			"description": "TARI 2021"
		},
		"transferList": [
			{
				"idTransfer": "1",
				"fiscalCodePA": "77777777777",
				"companyName": "Pa Salvo",
				"amount": "70.0",
				"transferCategory": "0101101IM",
				"remittanceInformation": "TARI Comune EC_TE"
			}
		],
		"eventStatus": "DONE"
	}
	return json_event
}

function createReceipt(id) {
	let receipt =
	{
		"eventId": id,
		"eventData": {
			"payerFiscalCode": "JHNDOE00A01F205N",
			"debtorFiscalCode": "JHNDOE00A01F205N"
		},
		"status": "INSERTED",
		"numRetry": 0,
		"id": id,
		"_rid": "Z9AJAMdamqNjAAAAAAAAAA==",
		"_self": "dbs/Z9AJAA==/colls/Z9AJAMdamqM=/docs/Z9AJAMdamqNjAAAAAAAAAA==/",
		"_etag": "\"08007a84-0000-0d00-0000-648b1e510000\"",
		"_attachments": "attachments/",
		"_ts": 1686838865
	}
	return receipt
}

module.exports = {
	createEvent, sleep, createReceipt, createEventForPoisonQueue, createEventForQueue
}