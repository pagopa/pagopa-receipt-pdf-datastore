export const SIM_TEST_CF = "AAAAAA00A00A000A";
export const TOKENIZED_SIM_TEST_CF = "cd07268c-73e8-4df4-8305-a35085e32eff";

export function randomString(length, charset) {
    let res = '';
    while (length--) res += charset[(Math.random() * charset.length) | 0];
    return res;
}

export function createEvent(id, customCF) {
	let json_event = {
		"id": id,
		"version": "2",
		"idPaymentManager": "54927408",
		"complete": "false",
		"receiptId": "9851395f09544a04b288202299193ca6",
		"missingInfo": [
			"psp.pspPartitaIVA",
			"paymentInfo.primaryCiIncurredFee",
			"paymentInfo.idBundle",
			"paymentInfo.idCiBundle"
		],
		"debtorPosition": {
			"modelType": "2",
			"noticeNumber": "310391366991197059",
			"iuv": "10391366991197059"
		},
		"creditor": {
			"idPA": "66666666666",
			"idBrokerPA": "66666666666",
			"idStation": "66666666666_08",
			"companyName": "PA paolo",
			"officeName": "office"
		},
		"psp": {
			"idPsp": "60000000001",
			"idBrokerPsp": "60000000001",
			"idChannel": "60000000001_08",
			"psp": "PSP Paolo",
			"pspFiscalCode": "CF60000000006",
			"channelDescription": "app"
		},
		"debtor": {
			"fullName": "paGetPaymentName",
			"entityUniqueIdentifierType": "G",
			"entityUniqueIdentifierValue": customCF ? customCF : randomString(11, "0123456789"),
			"streetName": "paGetPaymentStreet",
			"civicNumber": "paGetPayment99",
			"postalCode": "20155",
			"city": "paGetPaymentCity",
			"stateProvinceRegion": "paGetPaymentState",
			"country": "IT",
			"eMail": "paGetPayment@test.it"
		},
		"payer": {
			"fullName": "name",
			"entityUniqueIdentifierType": "G",
			"entityUniqueIdentifierValue": "JHNDOE00A01F205S",
			"streetName": "street",
			"civicNumber": "civic",
			"postalCode": "postal",
			"city": "city",
			"stateProvinceRegion": "state",
			"country": "IT",
			"eMail": "prova@test.it"
		},
		"paymentInfo": {
			"paymentDateTime": "2023-03-17T16:37:36.955813",
			"applicationDate": "2021-12-12",
			"transferDate": "2021-12-11",
			"dueDate": "2021-12-12",
			"paymentToken": "9851395f09544a04b288202299193ca6",
			"amount": "10.0",
			"fee": "2.0",
			"totalNotice": "1",
			"paymentMethod": "creditCard",
			"touchpoint": "app",
			"remittanceInformation": "TARI 2021",
			"description": "TARI 2021",
			"metadata": [
				{
					"key": "1",
					"value": "22"
				}
			]
		},
		"transferList": [
			{
				"idTransfer": "1",
				"fiscalCodePA": "66666666666",
				"companyName": "PA paolo",
				"amount": "10.0",
				"transferCategory": "paGetPaymentTest",
				"remittanceInformation": "/RFB/00202200000217527/5.00/TXT/"
			}
		],
		"transactionDetails": {
			"user": {
				"fullName": "John Doe",
				"type": "F",
				"fiscalCode": "JHNDOE00A01F205N",
				"notificationEmail": "john.doe@mail.it",
				"userId": "1234",
				"userStatus": "11",
				"userStatusDescription": "REGISTERED_SPID"
			},
			"transaction": {
				"idTransaction": "123456",
				"transactionId": "123456",
				"grandTotal": 0,
				"amount": 0,
				"fee": 0
			}
		},
		"timestamp": 1679067463501,
		"properties": {
			"diagnostic-id": "00-f70ef3167cffad76c6657a67a33ee0d2-61d794a75df0b43b-01",
			"serviceIdentifier": "NDP002SIT"
		},
		"eventStatus": "DONE",
		"eventRetryEnrichmentCount": 0
	}
	return json_event
}
