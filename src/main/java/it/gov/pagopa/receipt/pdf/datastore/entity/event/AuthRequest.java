package it.gov.pagopa.receipt.pdf.datastore.entity.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthRequest {
	private String authOutcome;
	private String guid;
	private String correlationId;
	private String error;
	@JsonProperty(value="auth_code")
	private String authCode;
}
