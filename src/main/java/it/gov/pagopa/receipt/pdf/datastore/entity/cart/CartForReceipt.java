package it.gov.pagopa.receipt.pdf.datastore.entity.cart;

import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.IOMessageData;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class CartForReceipt {

    private String eventId;
    private String id;
    private String version;
    private Payload payload;
    private CartStatusType status;
    private IOMessageData idMessageDebtor;
    private int numRetry;
    private int notificationNumRetry;
    private ReasonError reasonErr;
    private long inserted_at;
    private long generated_at;
    private long notified_at;
}
