const { QueueServiceClient } = require("@azure/storage-queue");

const connStr                  = process.env.RECEIPTS_STORAGE_CONN_STRING || "";
const receiptQueueName         = process.env.RECEIPT_QUEUE_NAME;
const poisonQueueName          = process.env.POISON_QUEUE_NAME;

const queueServiceClient       = QueueServiceClient.fromConnectionString(connStr);
const receiptQueueClient       = queueServiceClient.getQueueClient(receiptQueueName);
const errorReceiptQueueClient  = queueServiceClient.getQueueClient(poisonQueueName);

async function putMessageOnReceiptQueue(message) {
    // Send a message into the queue using the sendMessage method.
    message = btoa(JSON.stringify(message));
    return await receiptQueueClient.sendMessage(message);
}

async function putMessageOnPoisonQueue(message) {
    // Send a message into the queue using the sendMessage method.
    message = btoa(JSON.stringify(message));
    return await errorReceiptQueueClient.sendMessage(message);
}

module.exports = {
    putMessageOnPoisonQueue, putMessageOnReceiptQueue
}