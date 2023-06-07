package it.gov.pagopa.receipt.pdf.datastore.entities.event;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionDetails {
	private String origin;
	private User user;
	private Transaction transaction;
	private WalletItem wallet;
}
