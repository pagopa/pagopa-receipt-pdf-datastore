package it.gov.pagopa.receipt.pdf.datastore.exception;

import com.microsoft.azure.functions.HttpStatus;

/**
 * Thrown in case no receipt is found in the CosmosDB container
 */
public class BizEventNotFoundException extends BizEventException {

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     */
    public BizEventNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     * @param cause   Exception thrown
     */
    public BizEventNotFoundException(String message, Throwable cause) {
        super(message, cause, HttpStatus.NOT_FOUND);
    }
}


