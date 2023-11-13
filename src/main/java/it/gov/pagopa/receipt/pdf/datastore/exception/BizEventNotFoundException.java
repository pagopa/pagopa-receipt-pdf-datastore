package it.gov.pagopa.receipt.pdf.datastore.exception;

/** Thrown in case no receipt is found in the CosmosDB container */
public class BizEventNotFoundException extends Exception{

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     */
    public BizEventNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     * @param cause Exception thrown
     */
    public BizEventNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}


