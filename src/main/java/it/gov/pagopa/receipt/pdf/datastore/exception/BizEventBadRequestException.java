package it.gov.pagopa.receipt.pdf.datastore.exception;

import com.microsoft.azure.functions.HttpStatus;
import lombok.Getter;

/**
 * Thrown in case no receipt is found in the CosmosDB container
 */
public class BizEventBadRequestException extends BizEventException {

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     */
    public BizEventBadRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     * @param cause   Exception thrown
     */
    public BizEventBadRequestException(String message, Throwable cause) {
        super(message, cause, HttpStatus.BAD_REQUEST);
    }
}


