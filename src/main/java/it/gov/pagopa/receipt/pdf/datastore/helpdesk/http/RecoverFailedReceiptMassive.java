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
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.exception.InvalidParameterException;
import it.gov.pagopa.receipt.pdf.datastore.model.MassiveRecoverResult;
import it.gov.pagopa.receipt.pdf.datastore.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.datastore.service.HelpdeskService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.HelpdeskServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static it.gov.pagopa.receipt.pdf.datastore.utils.HelpdeskUtils.buildErrorResponse;
import static it.gov.pagopa.receipt.pdf.datastore.utils.HelpdeskUtils.validateReceiptStatusParam;


/**
 * Azure Functions with Azure Http trigger.
 */
public class RecoverFailedReceiptMassive {

    private final Logger logger = LoggerFactory.getLogger(RecoverFailedReceiptMassive.class);

    private final HelpdeskService helpdeskService;

    public RecoverFailedReceiptMassive() {
        this.helpdeskService = new HelpdeskServiceImpl();
    }

    RecoverFailedReceiptMassive(HelpdeskService helpdeskService) {
        this.helpdeskService = helpdeskService;
    }

    /**
     * This function will be invoked when an Http Trigger occurs.
     * <p>
     * It recovers all the receipts with the specified status that has to be one of:
     * <ul>
     *     <li>{@link ReceiptStatusType#INSERTED}</li>
     *     <li>{@link ReceiptStatusType#FAILED}</li>
     *     <li>{@link ReceiptStatusType#NOT_QUEUE_SENT}</li>
     * </ul>
     * <p>
     * It creates the receipts if not exist and send on queue the event in order to proceed with the receipt generation.
     *
     * @return response with {@link HttpStatus#OK} if the operation succeeded
     */
    @FunctionName("RecoverFailedReceiptMassive")
    public HttpResponseMessage run(
            @HttpTrigger(name = "RecoverFailedReceiptMassiveTrigger",
                    methods = {HttpMethod.POST},
                    route = "receipts/recover-failed",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    containerName = "receipts",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<Receipt>> documentdb,
            final ExecutionContext context
    ) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        // Get named parameter
        String statusParam = request.getQueryParameters().get("status");
        ReceiptStatusType status;
        try {
            status = validateReceiptStatusParam(statusParam);
        } catch (InvalidParameterException e) {
            return buildErrorResponse(request, HttpStatus.BAD_REQUEST, e.getMessage());
        }

        if (status == null || !status.isAFailedDatastoreStatus()) {
            String message = String.format("The provided status %s is not among the processable" +
                    "statuses (INSERTED, NOT_QUEUE_SENT, FAILED).", status);
            return buildErrorResponse(request, HttpStatus.UNPROCESSABLE_ENTITY, message);
        }

        MassiveRecoverResult recoverResult = this.helpdeskService.massiveRecoverFailedReceipt(status);

        int successCounter = recoverResult.getSuccessCounter();
        int errorCounter = recoverResult.getErrorCounter();

        if (errorCounter > 0) {
            documentdb.setValue(recoverResult.getFailedReceiptList());

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