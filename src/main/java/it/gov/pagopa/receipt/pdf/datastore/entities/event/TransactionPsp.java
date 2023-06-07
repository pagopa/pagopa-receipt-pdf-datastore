package it.gov.pagopa.receipt.pdf.datastore.entities.event;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionPsp {
	private String idChannel;
	private String businessName;
	private String serviceName;
}
