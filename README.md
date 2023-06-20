# pagoPA Receipt-pdf-datastore

---

## Run locally with Docker
`docker build -t pagopa-functions-template .`

`cp .env.example .env`

and replace in `.env` with correct values, then typing :

`docker run -p 80:80 --env-file=./.env pagopa-functions-template`

### Test
`curl http://localhost:8999/example`

## Run locally with Maven

On terminal and  typing :

`cp local.settings.json.example local.settings.json`

then replace env variables with correct values
(if there is NOT default value, the variable HAS to be defined)

| VARIABLE                          | USAGE                                                                            | DEFAULT VALUE |
|-----------------------------------|----------------------------------------------------------------------------------|:-------------:|
| `RECEIPT_QUEUE_CONN_STRING`       | Connection string to the Receipt Queue                                           |               |
| `RECEIPT_QUEUE_TOPIC`             | Topic name of the Receipt Queue                                                  |               |
| `RECEIPT_QUEUE_DELAY`             | Delay, in seconds, the visibility of the messages in the queue                   |       1       |
| `RECEIPT_QUEUE_MAX_RETRY`         | Number of retry to complete the generation process before being tagged as FAILED |       5       |
| `BLOB_STORAGE_ACCOUNT_ENDPOINT`   | Endpoint to the Receipt Blob Storage                                             |               |
| `BLOB_STORAGE_CONN_STRING`        | Connection string of the Receipt Blob Storage                                    |               |
| `BLOB_STORAGE_CONTAINER_NAME`     | Container name of the Receipt container in the Blob Storage                      |               |
| `COSMOS_BIZ_EVENT_CONN_STRING`    | Connection string to the BizEvent CosmosDB                                       |               |
| `COSMOS_RECEIPTS_CONN_STRING`     | Connection string to the Receipt CosmosDB                                        |               |
| `COSMOS_RECEIPT_SERVICE_ENDPOINT` | Endpoint to the Receipt CosmosDB                                                 |               |
| `COSMOS_RECEIPT_KEY`              | Key to the Receipt CosmosDB                                                      |               |
| `COSMOS_RECEIPT_DB_NAME`          | Database name of the Receipt database in CosmosDB                                |               |
| `COSMOS_RECEIPT_CONTAINER_NAME`   | Container name of the Receipt container in CosmosDB                              |               |
| `PDF_ENGINE_ENDPOINT`             | Endpoint to the PDF engine                                                       |               |
| `OCP_APIM_SUBSCRIPTION_KEY`       | Auth key for Azure to access the PDF Engine                                      |               |


> to doc details about AZ fn config see [here](https://stackoverflow.com/questions/62669672/azure-functions-what-is-the-purpose-of-having-host-json-and-local-settings-jso)

`mvn clean package`

`mvn azure-functions:run`

### Test
`curl http://localhost:7071/example`

---

Configure the SonarCloud project :point_right: [guide](https://sonarcloud.io/project/overview?id=pagopa_pagopa-receipt-pdf-datastore).