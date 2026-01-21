package it.gov.pagopa.receipt.pdf.datastore.exception;

import com.microsoft.azure.functions.HttpStatus;
import lombok.Getter;

/**
 * Thrown in case of BizEvent exception
 */
@Getter
public class BizEventException extends Exception {

    private final HttpStatus httpStatus;

    /**
     * Constructs new exception with provided message
     *
     * @param message Detail message
     */
    public BizEventException(String message) {
        super(message);
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * Constructs new exception with provided message and HTTP status code
     *
     * @param message Detail message
     * @param httpStatus HTTP status code
     */
    public BizEventException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     * @param cause   Exception thrown
     */
    public BizEventException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * Constructs new exception with provided message, cause and HTTP status code
     *
     * @param message Detail message
     * @param cause   Exception thrown
     * @param httpStatus HTTP status code
     */
    public BizEventException(String message, Throwable cause, HttpStatus httpStatus) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }
}


