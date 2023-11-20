package it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration;

public enum ReasonErrorCode {
    ERROR_QUEUE(902),
    ERROR_COSMOS(904),
    ERROR_PDV_IO(800),
    ERROR_PDV_UNEXPECTED(801),
    ERROR_PDV_MAPPING(802);
    ;

    private final int code;

    ReasonErrorCode(int code){
        this.code = code;
    }

    public int getCode(){
        return this.code;
    }
}
