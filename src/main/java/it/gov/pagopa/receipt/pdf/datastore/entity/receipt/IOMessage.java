package it.gov.pagopa.receipt.pdf.datastore.entity.receipt;

import it.gov.pagopa.receipt.pdf.datastore.entity.event.enumeration.UserType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IOMessage {

    String id;
    String messageId;
    String eventId;
    UserType userType;
}
