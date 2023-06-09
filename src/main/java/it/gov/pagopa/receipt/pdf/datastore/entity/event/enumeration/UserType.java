package it.gov.pagopa.receipt.pdf.datastore.entity.event.enumeration;

import com.google.api.client.util.NullValue;
import com.google.api.client.util.Value;


public enum UserType {
	@NullValue
	UNKNOWN,
	@Value("F")
    F, 
    @Value("G")
    G
}
