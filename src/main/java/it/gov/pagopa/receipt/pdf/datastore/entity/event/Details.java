package it.gov.pagopa.receipt.pdf.datastore.entity.event;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Details {
	private String blurredNumber;
	private String holder;
	private String circuit; 
}
