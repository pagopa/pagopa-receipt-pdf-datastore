### Review
```sh
docker build -f DockerfileReview -t exec-node .

docker run --rm --name initToRunk6 \
-e RECEIPT_COSMOS_SUBSCRIPTION_KEY=${RECEIPT_COSMOS_SUBSCRIPTION_KEY} \
-e ENVIRONMENT_STRING="${ENVIRONMENT_STRING}" \
exec-node 
```
### TearDown
```sh
docker build -f DockerfileTeardown -t exec-node .

docker run --rm --name initToRunk6 \
-e BLOB_STORAGE_CONN_STRING=${BLOB_STORAGE_CONN_STRING} \
-e RECEIPT_COSMOS_SUBSCRIPTION_KEY=${RECEIPT_COSMOS_SUBSCRIPTION_KEY} \
-e BIZEVENT_COSMOS_SUBSCRIPTION_KEY=${BIZEVENT_COSMOS_SUBSCRIPTION_KEY} \
-e ENVIRONMENT_STRING="${ENVIRONMENT_STRING}" \
exec-node 
```



