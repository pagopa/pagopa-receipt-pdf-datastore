package it.gov.pagopa.receipt.pdf.datastore.entity.cart;

import java.util.Set;

public enum CartStatusType {

    WAITING_FOR_BIZ_EVENT,
    NOT_QUEUE_SENT,
    INSERTED,
    RETRY,
    GENERATED,
    SIGNED,
    FAILED,
    IO_NOTIFIED,
    IO_ERROR_TO_NOTIFY,
    IO_NOTIFIER_RETRY,
    UNABLE_TO_SEND,
    NOT_TO_NOTIFY,
    TO_REVIEW;

    private static final Set<CartStatusType> DATASTORE_FAILED_STATUS = Set.of(
            NOT_QUEUE_SENT,
            INSERTED,
            FAILED
    );

    private static final Set<CartStatusType> NOTIFICATION_FAILED_STATUS = Set.of(
            GENERATED,
            IO_ERROR_TO_NOTIFY
    );

    public boolean isNotAFailedDatastoreStatus() {
        return !DATASTORE_FAILED_STATUS.contains(this);
    }

    public boolean isNotANotificationFailedStatus() {
        return !NOTIFICATION_FAILED_STATUS.contains(this);
    }
}
