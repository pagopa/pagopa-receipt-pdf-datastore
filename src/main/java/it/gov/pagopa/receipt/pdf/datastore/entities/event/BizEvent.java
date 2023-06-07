package it.gov.pagopa.receipt.pdf.datastore.entities.event;

import it.gov.pagopa.receipt.pdf.datastore.entities.event.enumeration.StatusType;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BizEvent {
	private String id;
	private String version;
	private String idPaymentManager;
	private String complete;
	private String receiptId;
	private List<String> missingInfo;
	private DebtorPosition debtorPosition;
	private Creditor creditor;
	private Psp psp;
	private Debtor debtor;
	private Payer payer;
	private PaymentInfo paymentInfo;
	private List<Transfer> transferList;
	private TransactionDetails transactionDetails;
	private Long timestamp;  // to be valued with ZonedDateTime.now().toInstant().toEpochMilli();
	private Map<String, Object> properties;
	
	// internal management field
	@Builder.Default
	private StatusType eventStatus = StatusType.NA;
	@Builder.Default
	private Integer eventRetryEnrichmentCount = 0;
	@Builder.Default
	private Boolean eventTriggeredBySchedule = Boolean.FALSE;
	private String eventErrorMessage;
	
}
