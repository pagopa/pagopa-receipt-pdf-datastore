# K6 tests for _BizEventsToDatastore_ project

[k6](https://k6.io/) is a load testing tool. 👀 See [here](https://k6.io/docs/get-started/installation/) to install it.

  - [01. Event hub biz event process](#01-event-hub-biz-event-process)

This is a set of [k6](https://k6.io) tests related to the _Biz Events to Datastore_ initiative.

To invoke k6 test passing parameter use -e (or --env) flag:

```
-e MY_VARIABLE=MY_VALUE
```

## 01. Event hub biz event process

Call to test the biz event processor:

```
k6 run --env VARS=local.environment.json --env TEST_TYPE=./test-types/load.json --env COSMOS_DB_SUBSCRIPTION_KEY=<your-secret> --env EVENT_HUB_SUBSCRIPTION_KEY=<your-secret> bizevent_processor.js 
```

where the mean of the environment variables is:

```json
  "environment": [
    {
      "env": "local",
      "cosmosDBURI": "https://pagopa-d-weu-bizevents-ds-cosmos-account.documents.azure.com/",
      "databaseID":"db",
      "containerID":"biz-events-test",
      "eventHubNamespace":"",
      "eventHubName":"",
      "eventHubSender":"",
      "processTime":""
    }
  ]
```

`cosmosDBURI`: Cosmos DB url to access Cosmos DB REST API

`databaseID`: database name to access Cosmos DB REST API

`containerID`: collection name to access Cosmos DB REST API

`eventHubNamespace`: service bus namespace to access the Event Hubs service REST API

`eventHubName`: Event Hub name to create Event Hub path to access the Event Hubs service REST API

`eventHubSender`: Event Hub sender name, used to publish events on the Event Hub

`processTime`: boundary time taken by azure function to fetch the payment event and save it in the datastore