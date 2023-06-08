package it.gov.pagopa.receipt.pdf.datastore.entities.receipt.enumeration;

public enum ReasonErrorCode {
    ERROR_QUEUE(902), ERROR_BLOB_STORAGE(901);

    private int code;

    ReasonErrorCode(int code){
        this.code = code;
    }

    public int getCode(){
        return this.code;
    }
}
