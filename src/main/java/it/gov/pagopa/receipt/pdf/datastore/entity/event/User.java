package it.gov.pagopa.receipt.pdf.datastore.entity.event;

import it.gov.pagopa.receipt.pdf.datastore.entity.event.enumeration.UserType;
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
