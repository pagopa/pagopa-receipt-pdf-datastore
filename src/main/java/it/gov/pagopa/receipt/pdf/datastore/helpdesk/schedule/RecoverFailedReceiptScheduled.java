package it.gov.pagopa.receipt.pdf.datastore.helpdesk.schedule;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.model.MassiveRecoverResult;
import it.gov.pagopa.receipt.pdf.datastore.service.HelpdeskService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.HelpdeskServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Azure Functions with Timer trigger.
 */
public class RecoverFailedReceiptScheduled {

    private final Logger logger = LoggerFactory.getLogger(RecoverFailedReceiptScheduled.class);

    private final boolean isEnabled = Boolean.parseBoolean(System.getenv().getOrDefault("FAILED_AUTORECOVER_ENABLED", "true"));

    private final HelpdeskService helpdeskService;

    public RecoverFailedReceiptScheduled() {
        this.helpdeskService = new HelpdeskServiceImpl();
    }

    RecoverFailedReceiptScheduled(HelpdeskService helpdeskService) {
        this.helpdeskService = helpdeskService;
    }

    /**
     * This function will be invoked periodically according to the specified schedule.
     * <p>
     * It recovers all the receipts with the following status:
     * <ul>
     *     <li>{@link ReceiptStatusType#INSERTED}</li>
     *     <li>{@link ReceiptStatusType#FAILED}</li>
     *     <li>{@link ReceiptStatusType#NOT_QUEUE_SENT}</li>
     * </ul>
     * <p>
     * It creates the receipts if not exist and send on queue the event in order to proceed with the receipt generation.
     */
    @FunctionName("RecoverFailedReceiptScheduled")
    public void run(
            @TimerTrigger(name = "timerInfoFailed", schedule = "%RECOVER_FAILED_CRON%") String timerInfo,
            @CosmosDBOutput(
                    name = "ReceiptDatastore",
                    databaseName = "db",
                    containerName = "receipts",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<Receipt>> documentdb,
            final ExecutionContext context
    ) {
        if (isEnabled) {
            logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

            List<Receipt> failedReceipts = new ArrayList<>();
            failedReceipts.addAll(recover(ReceiptStatusType.INSERTED));
            failedReceipts.addAll(recover(ReceiptStatusType.FAILED));
            failedReceipts.addAll(recover(ReceiptStatusType.NOT_QUEUE_SENT));

            documentdb.setValue(failedReceipts);
        }
    }

    private List<Receipt> recover(ReceiptStatusType status) {
        MassiveRecoverResult recoverResult = this.helpdeskService.massiveRecoverByStatus(status);

        int successCounter = recoverResult.getSuccessCounter();
        int errorCounter = recoverResult.getErrorCounter();

        if (errorCounter > 0) {
            logger.error("Recovered {} cart receipts but {} encountered an error.", successCounter, errorCounter);
            return recoverResult.getFailedReceiptList();
        }

        return Collections.emptyList();
    }
}