package it.gov.pagopa.receipt.pdf.datastore.service.impl;

import com.azure.cosmos.models.FeedResponse;
import it.gov.pagopa.receipt.pdf.datastore.client.CartReceiptsCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.ReceiptCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.CartReceiptsCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.ReceiptCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.IOMessage;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReceiptError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.CartNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.IoMessageNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.exception.ReceiptNotFoundException;
import it.gov.pagopa.receipt.pdf.datastore.service.ReceiptCosmosService;

public class ReceiptCosmosServiceImpl implements ReceiptCosmosService {

    private final ReceiptCosmosClient receiptCosmosClient;
    private final CartReceiptsCosmosClient cartReceiptsCosmosClient;

    public ReceiptCosmosServiceImpl() {
        this.receiptCosmosClient = ReceiptCosmosClientImpl.getInstance();
        this.cartReceiptsCosmosClient = CartReceiptsCosmosClientImpl.getInstance();
    }

    ReceiptCosmosServiceImpl(ReceiptCosmosClient receiptCosmosClient, CartReceiptsCosmosClient cartReceiptsCosmosClient) {
        this.receiptCosmosClient = receiptCosmosClient;
        this.cartReceiptsCosmosClient = cartReceiptsCosmosClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Receipt getReceipt(String eventId) throws ReceiptNotFoundException {
        Receipt receipt;
        try {
            receipt = this.receiptCosmosClient.getReceiptDocument(eventId);
        } catch (ReceiptNotFoundException e) {
            String errorMsg = String.format("Receipt not found with the biz-event id %s", eventId);
            throw new ReceiptNotFoundException(errorMsg, e);
        }

        return receipt;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReceiptError getReceiptError(String eventId) throws ReceiptNotFoundException {
        ReceiptError receipt;
        try {
            receipt = this.receiptCosmosClient.getReceiptError(eventId);
        } catch (ReceiptNotFoundException e) {
            String errorMsg = String.format("Receipt error not found with the biz-event id %s", eventId);
            throw new ReceiptNotFoundException(errorMsg, e);
        }

        if (receipt == null) {
            String errorMsg = String.format("Receipt error retrieved with the biz-event id %s is null", eventId);
            throw new ReceiptNotFoundException(errorMsg);
        }
        return receipt;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<FeedResponse<Receipt>> getNotNotifiedReceiptByStatus(
            String continuationToken,
            Integer pageSize,
            ReceiptStatusType statusType
    ) {
        if (statusType == null) {
            throw new IllegalArgumentException("at least one status must be specified");
        }
        if (statusType.equals(ReceiptStatusType.IO_ERROR_TO_NOTIFY)) {
            return this.receiptCosmosClient.getIOErrorToNotifyReceiptDocuments(continuationToken, pageSize);
        }
        if (statusType.equals(ReceiptStatusType.GENERATED)) {
            return this.receiptCosmosClient.getGeneratedReceiptDocuments(continuationToken, pageSize);
        }
        String errMsg = String.format("Unexpected status for retrieving not notified receipt: %s", statusType);
        throw new IllegalStateException(errMsg);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterable<FeedResponse<Receipt>> getFailedReceiptByStatus(
            String continuationToken,
            Integer pageSize,
            ReceiptStatusType statusType
    ) {
        if (statusType == null) {
            throw new IllegalArgumentException("at least one status must be specified");
        }
        if (statusType.equals(ReceiptStatusType.FAILED) || statusType.equals(ReceiptStatusType.NOT_QUEUE_SENT)) {
            return this.receiptCosmosClient.getFailedReceiptDocuments(continuationToken, pageSize);
        }
        if (statusType.equals(ReceiptStatusType.INSERTED)) {
            return this.receiptCosmosClient.getInsertedReceiptDocuments(continuationToken, pageSize);
        }
        String errMsg = String.format("Unexpected status for retrieving failed receipt: %s", statusType);
        throw new IllegalStateException(errMsg);
    }
}