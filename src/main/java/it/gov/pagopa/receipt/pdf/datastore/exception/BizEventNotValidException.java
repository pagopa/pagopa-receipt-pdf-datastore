package it.gov.pagopa.receipt.pdf.datastore.exception;

/** Thrown in case the message triggering the function GenerateReceiptPdf is an invalid biz-event */
public class BizEventNotValidException extends Exception{

    /**
     * Constructs new exception with provided message and cause
     *
     * @param message Detail message
     */
    public BizEventNotValidException(String message) {
        super(message);
    }
}