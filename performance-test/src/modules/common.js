export const SIM_TEST_CF = "SIMTESTCF";

export function randomString(length, charset) {
    let res = '';
    while (length--) res += charset[(Math.random() * charset.length) | 0];
    return res;
}

export function createEvent(id, customCF) {
    const idPA = randomString(11, "0123456789");
    const idPSP = randomString(11, "0123456789");
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
            "noticeNumber": randomString(18, "0123456789"),
            "iuv": randomString(17, "0123456789")
        },
        "creditor": {
            "idPA": idPA,
            "idBrokerPA": idPA,
            "idStation": `${idPA}_08`,
            "companyName": "PA test_company"
        },
        "psp": {
            "idPsp": idPSP,
            "idBrokerPsp": idPSP,
            "idChannel": `${idPSP}_08`,
            "psp": "test_PSP"
        },
		"debtor": {
			"fullName": "John Doe",
			"entityUniqueIdentifierType": "F",
            "entityUniqueIdentifierValue": customCF ? customCF : randomString(11, "0123456789"),
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
                "fiscalCodePA": idPA,
                "companyName": "PA test_company",
                "amount": "2.00",
                "transferCategory": "paTest",
                "remittanceInformation": "/RFB/00202200000217527/5.00/TXT/"
            },
            {
                "fiscalCodePA": randomString(11, "0123456789"),
                "companyName": "PA test_company",
                "amount": "8.00",
                "transferCategory": "paTest",
                "remittanceInformation": "/RFB/00202200000217527/5.00/TXT/"
            }
		],
		"eventStatus": "DONE",
		"eventRetryEnrichmentCount": 0
	}
    return json_event;
}
