# K6 tests for _BizEventsToDatastore_ project

[k6](https://k6.io/) is a load testing tool. 👀 See [here](https://k6.io/docs/get-started/installation/) to install it.

- [01. Receipt datastore function](#01-receipt-datastore-function)

This is a set of [k6](https://k6.io) tests related to the _Biz Events to Datastore_ initiative.

To invoke k6 test passing parameter use -e (or --env) flag:

```
-e MY_VARIABLE=MY_VALUE
```

## 01. Receipt datastore function

Start CosmosDB service server:

```
node src/cosmos-service/server.js
```

Open another terminal and test the receipt datastore function:

```
k6 run --env VARS=local.environment.json --env TEST_TYPE=./test-types/load.json --env BIZEVENT_COSMOS_DB_SUBSCRIPTION_KEY=<your-secret> --env RECEIPT_COSMOS_DB_SUBSCRIPTION_KEY=<your-secret> receipt_processor.js
```

where the mean of the environment variables is:

```json
  "environment": [
    {
      "env": "local",
      "cosmosServiceURI": "http://localhost:8079",
      "bizEventCosmosDBURI": "",
      "bizEventDatabaseID":"",
      "bizEventContainerID":"",
      "receiptCosmosDBURI": "",
      "receiptDatabaseID":"",
      "receiptContainerID":"",
      "processTime":""
    }
  ]
```

`cosmosServiceURI`: CosmosDB service server URI to access create/delete/get document APIs
`bizEventCosmosDBURI`: CosmosDB url to access Biz-events CosmosDB REST API
`bizEventDatabaseID`: database name to access Biz-events Cosmos DB REST API
`bizEventContainerID`: collection name to access Biz-events Cosmos DB REST API
`receiptCosmosDBURI`: CosmosDB url to access Receipts CosmosDB REST API
`receiptDatabaseID`: database name to access Receipts Cosmos DB REST API
`receiptContainerID`: collection name to access Receipts Cosmos DB REST API
`processTime`: boundary time taken by azure function to fetch the payment event and save it in the datastore
