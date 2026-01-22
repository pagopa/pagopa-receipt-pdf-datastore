package it.gov.pagopa.receipt.pdf.datastore.helpdesk.http;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.InvalidParameterException;
import it.gov.pagopa.receipt.pdf.datastore.model.MassiveCartRecoverResult;
import it.gov.pagopa.receipt.pdf.datastore.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.datastore.service.HelpdeskService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.HelpdeskServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static it.gov.pagopa.receipt.pdf.datastore.utils.HelpdeskUtils.buildErrorResponse;
import static it.gov.pagopa.receipt.pdf.datastore.utils.HelpdeskUtils.validateCartStatusParam;


/**
 * Azure Functions with Azure Http trigger.
 */
public class RecoverFailedCartReceiptMassive {

    private final Logger logger = LoggerFactory.getLogger(RecoverFailedCartReceiptMassive.class);

    private final HelpdeskService helpdeskService;

    public RecoverFailedCartReceiptMassive() {
        this.helpdeskService = new HelpdeskServiceImpl();
    }

    RecoverFailedCartReceiptMassive(HelpdeskService helpdeskService) {
        this.helpdeskService = helpdeskService;
    }

    /**
     * This function will be invoked when a Http Trigger occurs.
     * <p>
     * It recovers all the cart receipts with the specified status that has to be one of:
     * <ul>
     *  <li>{@link CartStatusType#INSERTED}</li>
     *  <li>{@link CartStatusType#FAILED}</li>
     *  <li>{@link CartStatusType#NOT_QUEUE_SENT}</li>
     * </ul>
     * <p>
     * It creates the cart receipts and send on queue the event in order to proceed with the receipt generation.
     *
     * @return response with {@link HttpStatus#OK} if the operation succeeded
     */
    @FunctionName("RecoverFailedCartReceiptMassive")
    public HttpResponseMessage run(
            @HttpTrigger(name = "RecoverFailedCartReceiptMassiveTrigger",
                    methods = {HttpMethod.POST},
                    route = "cart-receipts/recover-failed",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @CosmosDBOutput(
                    name = "CartReceiptDatastore",
                    databaseName = "db",
                    containerName = "cart-for-receipts",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<CartForReceipt>> documentdb,
            final ExecutionContext context
    ) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        // Get named parameter
        String statusParam = request.getQueryParameters().get("status");
        CartStatusType status;
        try {
            status = validateCartStatusParam(statusParam);
        } catch (InvalidParameterException e) {
            return buildErrorResponse(request, HttpStatus.BAD_REQUEST, e.getMessage());
        }

        if (status == null || !status.isAFailedDatastoreStatus()) {
            String message = String.format("The provided status %s is not among the processable" +
                    "statuses (INSERTED, NOT_QUEUE_SENT, FAILED).", status);
            return buildErrorResponse(request, HttpStatus.UNPROCESSABLE_ENTITY, message);
        }

        MassiveCartRecoverResult recoverResult = this.helpdeskService.massiveRecoverFailedCart(status);

        int successCounter = recoverResult.getSuccessCounter();
        int errorCounter = recoverResult.getErrorCounter();

        if (errorCounter > 0) {
            documentdb.setValue(recoverResult.getFailedCartList());

            String msg = String.format("Recovered %s receipts but %s encountered an error.", successCounter, errorCounter);
            return request
                    .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ProblemJson.builder()
                            .title("Partial OK")
                            .detail(msg)
                            .status(HttpStatus.MULTI_STATUS.value())
                            .build())
                    .build();
        }
        String responseMsg = String.format("Recovered %s receipts", successCounter);
        return request.createResponseBuilder(HttpStatus.OK)
                .body(responseMsg)
                .build();
    }
}