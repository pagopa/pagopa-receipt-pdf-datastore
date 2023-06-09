package it.gov.pagopa.receipt.pdf.datastore.entity.event;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DebtorPosition {
	private String modelType;
	private String noticeNumber;
	private String iuv;
}
