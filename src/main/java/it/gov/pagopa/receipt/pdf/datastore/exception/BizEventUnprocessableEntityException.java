package it.gov.pagopa.receipt.pdf.datastore.exception;

import com.microsoft.azure.functions.HttpStatus;

/**
 * Thrown in case no receipt is found in the CosmosDB container
 */
public class BizEventUnprocessableEntityException extends BizEventException {

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     */
    public BizEventUnprocessableEntityException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     * @param cause   Exception thrown
     */
    public BizEventUnprocessableEntityException(String message, Throwable cause) {
        super(message, cause, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}


