package it.gov.pagopa.receipt.pdf.datastore.exception;

/** Thrown in case a concurrent update happened while saving a cart on CosmosDB container */
public class CartConcurrentUpdateException extends Exception {

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     */
    public CartConcurrentUpdateException(String message) {
        super(message);
    }

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     * @param cause Exception thrown
     */
    public CartConcurrentUpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}


