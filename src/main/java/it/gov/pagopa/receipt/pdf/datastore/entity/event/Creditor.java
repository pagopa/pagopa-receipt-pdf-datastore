package it.gov.pagopa.receipt.pdf.datastore.entity.event;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Creditor {
	private String idPA;
	private String idBrokerPA;
	private String idStation;
	private String companyName;
	private String officeName;
}
