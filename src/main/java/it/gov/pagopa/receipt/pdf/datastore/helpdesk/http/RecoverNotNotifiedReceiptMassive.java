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
import it.gov.pagopa.receipt.pdf.datastore.model.ProblemJson;
import it.gov.pagopa.receipt.pdf.datastore.service.HelpdeskService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.HelpdeskServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


/**
 * Azure Functions with HTTP Trigger.
 */
public class RecoverNotNotifiedReceiptMassive {

    private final Logger logger = LoggerFactory.getLogger(RecoverNotNotifiedReceiptMassive.class);

    private final HelpdeskService helpdeskService;

    public RecoverNotNotifiedReceiptMassive() {
        this.helpdeskService = new HelpdeskServiceImpl();
    }

    RecoverNotNotifiedReceiptMassive(HelpdeskService helpdeskService) {
        this.helpdeskService = helpdeskService;
    }

    /**
     * This function will be invoked when an Http Trigger occurs.
     * <p>
     * It recovers all receipt with the provided status.
     * <p>
     * It recovers the receipt with failed notification ({@link ReceiptStatusType#IO_ERROR_TO_NOTIFY}) or notification
     * not triggered ({@link ReceiptStatusType#GENERATED} by clearing the errors and update the status to the
     * previous step ({@link ReceiptStatusType#GENERATED}).
     *
     * @return response with {@link HttpStatus#OK} if the operation succeeded
     */
    @FunctionName("RecoverNotNotifiedReceiptMassive")
    public HttpResponseMessage run(
            @HttpTrigger(name = "RecoverNotNotifiedMassiveTrigger",
                    methods = {HttpMethod.POST},
                    route = "receipts/recover-not-notified",
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    containerName = "receipts",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<Receipt>> documentReceipts,
            final ExecutionContext context
    ) {
        logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

        // Get named parameter
        String statusParam = request.getQueryParameters().get("status");
        ReceiptStatusType status;
        try {
            status = validateStatusParam(statusParam);
        } catch (InvalidParameterException e) {
            return request
                    .createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body(ProblemJson.builder()
                            .title(HttpStatus.BAD_REQUEST.name())
                            .detail(e.getMessage())
                            .status(HttpStatus.BAD_REQUEST.value())
                            .build())
                    .build();
        }

        List<Receipt> receiptList = this.helpdeskService.massiveRecoverNoNotified(status);
        if (receiptList.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.OK).body("No receipts to be recovered").build();
        }

        documentReceipts.setValue(receiptList);
        String msg = String.format("Recovered %s receipt with success", receiptList.size());
        return request.createResponseBuilder(HttpStatus.OK).body(msg).build();
    }

    private ReceiptStatusType validateStatusParam(String statusParam) throws InvalidParameterException {
        if (statusParam == null) {
            throw new InvalidParameterException("Please pass a status to recover");
        }

        ReceiptStatusType status;
        try {
            status = ReceiptStatusType.valueOf(statusParam);
        } catch (IllegalArgumentException e) {
            throw new InvalidParameterException("Please pass a valid status to recover", e);
        }

        if (!status.isANotificationFailedStatus()) {
            String message = String.format("The provided status %s is not among the processable" +
                    "statuses (GENERATED, IO_ERROR_TO_NOTIFY).", status);
            throw new InvalidParameterException(message);
        }
        return status;
    }
}
