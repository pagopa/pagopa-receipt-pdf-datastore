package it.gov.pagopa.receipt.pdf.datastore.entities.receipt;

import it.gov.pagopa.receipt.pdf.datastore.entities.event.enumeration.ReceiptStatusType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Receipt {

    private String idEvent;
    private EventData eventData;
    private IOMessageData ioMessageData;
    private ReceiptStatusType status;
    private ReceiptMetadata mdAttach;
    private ReceiptMetadata mdAttachPayer;
    private int numRetry;
    private ReasonError reasonErr;
}
