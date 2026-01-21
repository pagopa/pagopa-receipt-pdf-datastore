package it.gov.pagopa.receipt.pdf.datastore.utils;

import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.InvalidParameterException;
import it.gov.pagopa.receipt.pdf.datastore.model.ProblemJson;

import java.util.Optional;

public class HelpdeskUtils {

    public static HttpResponseMessage buildErrorResponse(
            HttpRequestMessage<Optional<String>> request,
            HttpStatus httpStatus,
            String errMsg
    ) {
        return request
                .createResponseBuilder(httpStatus)
                .body(ProblemJson.builder()
                        .title(httpStatus.name())
                        .detail(errMsg)
                        .status(httpStatus.value())
                        .build())
                .build();
    }

    public static CartStatusType validateCartStatusParam(String statusParam) throws InvalidParameterException {
        if (statusParam == null) {
            throw new InvalidParameterException("Please pass a status to recover");
        }

        CartStatusType status;
        try {
            status = CartStatusType.valueOf(statusParam);
        } catch (IllegalArgumentException e) {
            throw new InvalidParameterException("Please pass a valid status to recover", e);
        }

        return status;
    }


    public static ReceiptStatusType validateReceiptStatusParam(String statusParam) throws InvalidParameterException {
        if (statusParam == null) {
            throw new InvalidParameterException("Please pass a status to recover");
        }

        ReceiptStatusType status;
        try {
            status = ReceiptStatusType.valueOf(statusParam);
        } catch (IllegalArgumentException e) {
            throw new InvalidParameterException("Please pass a valid status to recover", e);
        }

        return status;
    }

    private HelpdeskUtils() {
    }
}
