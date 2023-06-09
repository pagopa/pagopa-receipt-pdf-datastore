package it.gov.pagopa.receipt.pdf.datastore.entity.event;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Psp {
	private String idPsp;
	private String idBrokerPsp;
	private String idChannel;
	private String psp;
	private String pspPartitaIVA;
	private String pspFiscalCode;
	private String channelDescription;
}
