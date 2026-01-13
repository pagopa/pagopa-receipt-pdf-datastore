package it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration;

import java.util.Set;

public enum ReceiptStatusType {
    NOT_QUEUE_SENT, INSERTED, RETRY, GENERATED, SIGNED, FAILED, IO_NOTIFIED, IO_ERROR_TO_NOTIFY, IO_NOTIFIER_RETRY, UNABLE_TO_SEND, NOT_TO_NOTIFY, TO_REVIEW;

    private static final Set<ReceiptStatusType> DATASTORE_FAILED_STATUS = Set.of(
            NOT_QUEUE_SENT,
            INSERTED,
            FAILED
    );

    private static final Set<ReceiptStatusType> NOTIFICATION_FAILED_STATUS = Set.of(
            GENERATED,
            IO_ERROR_TO_NOTIFY
    );

    public boolean isAFailedDatastoreStatus() {
        return DATASTORE_FAILED_STATUS.contains(this);
    }

    public boolean isANotificationFailedStatus() {
        return NOTIFICATION_FAILED_STATUS.contains(this);
    }
}
