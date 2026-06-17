export const SIM_TEST_CF = "JHNDOE00A01B157N";
export const TOKENIZED_SIM_TEST_CF = "4cdaa80f-fc52-45f5-a413-d6ebc4d0759e";

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
			"idPsp": "ABI36935",
			"idBrokerPsp": "03339200374",
			"idChannel": "03339200374_01",
			"psp": "PSP Paolo",
		},
		"debtor": {
			"fullName": "Debtor name",
			"entityUniqueIdentifierType": "G",
			"entityUniqueIdentifierValue": customCF ? customCF : randomString(11, "0123456789")
		},
		"payer": {
			"fullName": "Payer name",
			"entityUniqueIdentifierType": "G",
			"entityUniqueIdentifierValue": "JHNDOE00A01F205S"
		},
		"paymentInfo": {
			"paymentDateTime": getCurrentDateTime5Digits(),
			"paymentToken": "9851395f09544a04b288202299193ca6",
			"amount": "10.0",
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
				"name": "John",
				"surname": "Doe",
				"type": "F",
				"fiscalCode": "JHNDOE00A01F205S",
				"notificationEmail": "john.doe@mail.it",
				"userId": "1234",
				"userStatus": "11",
				"userStatusDescription": "REGISTERED_SPID"
			},
			"transaction": {
				"idTransaction": "123456",
				"transactionId": "123456",
				"grandTotal": 1200,
				"amount": 1000,
				"fee": 200,
				"creationDate": getCurrentDateTime9DigitsZ(),
				"origin": "IO"
			},
			"wallet": {
				"idWallet": 125714007,
				"walletType": "Card",
				"enableableFunctions": ["pagoPA", "BPD", "FA"],
				"pagoPa": true,
				"onboardingChannel": "IO",
				"favourite": false,
				"createDate": "2022-12-22T13:07:25Z",
				"info": {
					"type": "CP",
					"holder": "Card Holder name",
					"blurredNumber": "0403",
					"hashPan": "e88aadfd9f40e1482615fd3c8c44f05c53f93aed1bcea69e82b3e5e27668f822",
					"expireMonth": "06",
					"expireYear": "2026",
					"brand": "MASTERCARD",
					"brandLogo": "https://wisp2.pagopa.gov.it/wallet/assets/img/creditcard/carta_visa.png"
				}
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

/ Utility per padding con zeri a sinistra
function padLeft(num, size) {
	return String(num).padStart(size, '0');
}

/**
 * Formato locale:
 * "YYYY-MM-DDTHH:mm:ss.SSSSS"
 * Esempio: "2025-11-02T11:14:57.16758"
 */
function getCurrentDateTime5Digits() {
	const now = new Date();

	const year = now.getFullYear();
	const month = padLeft(now.getMonth() + 1, 2);
	const day = padLeft(now.getDate(), 2);
	const hour = padLeft(now.getHours(), 2);
	const minute = padLeft(now.getMinutes(), 2);
	const second = padLeft(now.getSeconds(), 2);

	const millis = padLeft(now.getMilliseconds(), 3); // "SSS"

	// 2 cifre extra semplici: riuso delle prime 2 cifre dei ms
	const extra = millis.slice(0, 2); // es. "167" -> "16"

	const fraction5 = millis + extra; // "SSSSS"

	return `${year}-${month}-${day}T${hour}:${minute}:${second}.${fraction5}`;
}

/**
 * Formato UTC:
 * "YYYY-MM-DDTHH:mm:ss.SSSSSSSSSZ"
 * Esempio: "2025-11-02T10:14:57.218496702Z"
 */
function getCurrentDateTime9DigitsZ() {
	const now = new Date();

	const year = now.getUTCFullYear();
	const month = padLeft(now.getUTCMonth() + 1, 2);
	const day = padLeft(now.getUTCDate(), 2);
	const hour = padLeft(now.getUTCHours(), 2);
	const minute = padLeft(now.getUTCMinutes(), 2);
	const second = padLeft(now.getUTCSeconds(), 2);

	const millis = padLeft(now.getUTCMilliseconds(), 3); // "SSS"

	// Ripetiamo i ms finché non arriviamo a 9 cifre
	// es. "218" -> "218218218" (9 cifre)
	const fraction9 = (millis + millis + millis).slice(0, 9);

	return `${year}-${month}-${day}T${hour}:${minute}:${second}.${fraction9}Z`;
}