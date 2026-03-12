package it.gov.pagopa.receipt.pdf.datastore.exception;

/**
 * Thrown in case an expected error occur in recover process
 */
public class RecoverFailureException extends RuntimeException {

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     */
    public RecoverFailureException(String message) {
        super(message);
    }

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     * @param cause   Exception thrown
     */
    public RecoverFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}


