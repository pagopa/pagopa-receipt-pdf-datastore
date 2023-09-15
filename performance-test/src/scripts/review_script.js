import { getDocumentByDebtorCF } from "../modules/datastore_client";
import { receiptContainerID, receiptCosmosDBPrimaryKey, receiptCosmosDBURI, receiptDatabaseID } from "./scripts_common";

const reviewReceiptsTimeToProcess = () => {
    let r = getDocumentByDebtorCF(receiptCosmosDBURI, receiptDatabaseID, receiptContainerID, receiptCosmosDBPrimaryKey, SIM_TEST_CF);

    let receipts = r?.json()?.Documents;
}

reviewReceiptsTimeToProcess();