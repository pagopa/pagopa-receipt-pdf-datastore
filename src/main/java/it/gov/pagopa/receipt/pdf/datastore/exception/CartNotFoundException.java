package it.gov.pagopa.receipt.pdf.datastore.exception;

/** Thrown in case no receipt is found in the CosmosDB container */
public class CartNotFoundException extends Exception {

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     */
    public CartNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     * @param cause Exception thrown
     */
    public CartNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}


