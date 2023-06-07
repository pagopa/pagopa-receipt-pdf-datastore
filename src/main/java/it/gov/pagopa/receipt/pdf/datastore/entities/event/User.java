package it.gov.pagopa.receipt.pdf.datastore.entities.event;

import it.gov.pagopa.receipt.pdf.datastore.entities.event.enumeration.UserType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
	private String fullName;
	private UserType type;
	private String fiscalCode;
	private String notificationEmail;
	private String userId;
	private String userStatus;
	private String userStatusDescription;
}
