package it.gov.pagopa.receipt.pdf.datastore.exception;

/**
 * Thrown in case an invalid parameter is provided in a helpdesk request
 */
public class InvalidParameterException extends Exception {

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     */
    public InvalidParameterException(String message) {
        super(message);
    }

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     * @param cause   Exception thrown
     */
    public InvalidParameterException(String message, Throwable cause) {
        super(message, cause);
    }
}


