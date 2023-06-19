package it.gov.pagopa.receipt.pdf.datastore.entity.event;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Info {
	private String type;
	private String blurredNumber;
	private String holder;
	private String expireMonth;
	private String expireYear;
	private String brand;
	private String issuerAbi;
	private String issuerName;
	private String label;
}
