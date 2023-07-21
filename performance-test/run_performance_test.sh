# sh run_performance_test.sh <local|dev|uat|prod> <load|stress|spike|soak|...> <script-name> <db-name> <biz-event-cosmos-subkey> <receipts-cosmos-subkey>

ENVIRONMENT=$1
TYPE=$2
SCRIPT=$3
DB_NAME=$4
BIZEVENT_COSMOS_DB_SUBSCRIPTION_KEY=$5
RECEIPT_COSMOS_DB_SUBSCRIPTION_KEY=$6

if [ -z "$ENVIRONMENT" ]
then
  echo "No env specified: sh run_performance_test.sh <local|dev|uat|prod> <load|stress|spike|soak|...> <script-name> <db-name> <biz-event-cosmos-subkey> <receipts-cosmos-subkey>"
  exit 1
fi

if [ -z "$TYPE" ]
then
  echo "No test type specified: sh run_performance_test.sh <local|dev|uat|prod> <load|stress|spike|soak|...> <script-name> <db-name> <biz-event-cosmos-subkey> <receipts-cosmos-subkey>"
  exit 1
fi
if [ -z "$SCRIPT" ]
then
  echo "No script name specified: sh run_performance_test.sh <local|dev|uat|prod> <load|stress|spike|soak|...> <script-name> <db-name> <biz-event-cosmos-subkey> <receipts-cosmos-subkey>"
  exit 1
fi

export env=${ENVIRONMENT}
export type=${TYPE}
export script=${SCRIPT}
export db_name=${DB_NAME}
export biz_event_cosmos_key=${BIZEVENT_COSMOS_DB_SUBSCRIPTION_KEY}
export receipts_cosmos_key=${RECEIPT_COSMOS_DB_SUBSCRIPTION_KEY}

stack_name=$(cd .. && basename "$PWD")
docker compose -p "${stack_name}-k6" up -d --remove-orphans --force-recreate --build
docker logs -f k6
docker stop nginx
