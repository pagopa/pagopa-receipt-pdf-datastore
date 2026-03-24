package it.gov.pagopa.receipt.pdf.datastore.helpdesk.http;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.InvalidParameterException;
import it.gov.pagopa.receipt.pdf.datastore.model.MassiveCartRecoverResult;
import it.gov.pagopa.receipt.pdf.datastore.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.datastore.service.HelpdeskService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.HelpdeskServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Optional;

import static it.gov.pagopa.receipt.pdf.datastore.utils.HelpdeskUtils.buildErrorResponse;
import static it.gov.pagopa.receipt.pdf.datastore.utils.HelpdeskUtils.validateCartStatusParam;

/**
 * Azure Functions with HTTP Trigger.
 */
public class RecoverNotNotifiedCartReceiptMassive {

    private final Logger logger = LoggerFactory.getLogger(RecoverNotNotifiedCartReceiptMassive.class);

    private final HelpdeskService helpdeskService;

    public RecoverNotNotifiedCartReceiptMassive() {
        this.helpdeskService = new HelpdeskServiceImpl();
    }

    RecoverNotNotifiedCartReceiptMassive(HelpdeskService helpdeskService) {
        this.helpdeskService = helpdeskService;
    }

    /**
     * This function will be invoked when a Http Trigger occurs.
     * <p>
     * It recovers all receipt with the provided status.
     * <p>
     * It recovers the receipt with failed notification ({@link CartStatusType#IO_ERROR_TO_NOTIFY}) or notification
     * not triggered ({@link CartStatusType#GENERATED} by clearing the errors and update the status to the
     * previous step ({@link CartStatusType#GENERATED}).
     *
     * @return response with {@link HttpStatus#OK} if the operation succeeded
     */
    @FunctionName("RecoverNotNotifiedCartReceiptMassive")
    public HttpResponseMessage run(
            @HttpTrigger(name = "RecoverNotNotifiedCartMassiveTrigger",
                    methods = {HttpMethod.POST},
                    route = "cart-receipts/recover-not-notified",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context
    ) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        // Get named parameter
        String statusParam = request.getQueryParameters().get("status");
        CartStatusType status;
        try {
            status = validateCartStatusParam(statusParam);
        } catch (InvalidParameterException e) {
            logger.warn("[{}]", context.getFunctionName(), e);
            return buildErrorResponse(request, HttpStatus.BAD_REQUEST, e.getMessage());
        }

        if (status == null || status.isNotANotificationFailedStatus()) {
            String message = String.format("The provided status %s is not among the processable" +
                    "statuses (GENERATED, IO_ERROR_TO_NOTIFY).", status);
            logger.warn("[{}] {}", context.getFunctionName(), message);
            return buildErrorResponse(request, HttpStatus.UNPROCESSABLE_ENTITY, message);
        }

        MassiveCartRecoverResult recoverResult = this.helpdeskService.massiveRecoverNoNotifiedCart(status);

        int successCounter = recoverResult.getSuccessCounter();
        int errorCounter = recoverResult.getErrorCounter();

        if (errorCounter > 0) {
            String msg = String.format("Recovered %s cart receipts but %s encountered an error.", successCounter, errorCounter);
            logger.error("[{}] {}", context.getFunctionName(), msg);
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title("Partial OK")
                            .detail(msg)
                            .status(HttpStatus.MULTI_STATUS.value())
                            .build())
                    .build();
        }
        String responseMsg = String.format("Recovered %s cart receipts with success", successCounter);
        return request.createResponseBuilder(HttpStatus.OK)
                .body(responseMsg)
                .build();
    }
}
