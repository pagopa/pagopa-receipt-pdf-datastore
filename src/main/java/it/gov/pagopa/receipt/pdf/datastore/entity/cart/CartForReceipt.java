package it.gov.pagopa.receipt.pdf.datastore.entity.cart;

import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.ReasonError;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@Data
public class CartForReceipt {

    private String eventId;
    private String id;
    private String version;
    private Payload payload;
    private CartStatusType status;
    private Set<String> cartPaymentId;
    private int numRetry;
    private int notificationNumRetry;
    private Integer totalNotice;
    private ReasonError reasonErr;
    private long inserted_at;
    private long generated_at;
    private long notified_at;
    private String _etag;
}
