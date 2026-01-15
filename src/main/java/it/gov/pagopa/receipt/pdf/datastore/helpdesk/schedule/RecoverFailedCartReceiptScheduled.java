package it.gov.pagopa.receipt.pdf.datastore.helpdesk.schedule;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.model.MassiveCartRecoverResult;
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
public class RecoverFailedCartReceiptScheduled {

    private final Logger logger = LoggerFactory.getLogger(RecoverFailedCartReceiptScheduled.class);

    private final boolean isEnabled = Boolean.parseBoolean(System.getenv().getOrDefault("FAILED_AUTORECOVER_ENABLED", "true"));

    private final HelpdeskService helpdeskService;

    public RecoverFailedCartReceiptScheduled() {
        this.helpdeskService = new HelpdeskServiceImpl();
    }

    RecoverFailedCartReceiptScheduled(HelpdeskService helpdeskService) {
        this.helpdeskService = helpdeskService;
    }

    /**
     * This function will be invoked periodically according to the specified schedule.
     * <p>
     * It recovers all the receipts with the following status:
     * <ul>
     *  <li>{@link CartStatusType#INSERTED}</li>
     *  <li>{@link CartStatusType#FAILED}</li>
     *  <li>{@link CartStatusType#NOT_QUEUE_SENT}</li>
     * </ul>
     * <p>
     * It recovers cart receipts and send on queue the event in order to proceed with the receipt generation.
     */
    @FunctionName("RecoverFailedCartReceiptScheduled")
    public void run(
            @TimerTrigger(name = "timerInfoFailed", schedule = "%RECOVER_FAILED_CRON%") String timerInfo,
            @CosmosDBOutput(
                    name = "CartReceiptDatastore",
                    databaseName = "db",
                    containerName = "cart-for-receipts",
                    connection = "COSMOS_RECEIPTS_CONN_STRING")
            OutputBinding<List<CartForReceipt>> documentdb,
            final ExecutionContext context
    ) {
        if (isEnabled) {
            logger.info("[{}] function called at {}", context.getFunctionName(), LocalDateTime.now());

            List<CartForReceipt> failedCart = new ArrayList<>();
            failedCart.addAll(recover(CartStatusType.INSERTED));
            failedCart.addAll(recover(CartStatusType.FAILED));
            failedCart.addAll(recover(CartStatusType.NOT_QUEUE_SENT));

            documentdb.setValue(failedCart);
        }
    }

    private List<CartForReceipt> recover(CartStatusType status) {
        MassiveCartRecoverResult recoverResult = this.helpdeskService.massiveRecoverByStatus(status);

        int successCounter = recoverResult.getSuccessCounter();
        int errorCounter = recoverResult.getErrorCounter();

        if (errorCounter > 0) {
            logger.warn("Recovered {} cart receipts but {} encountered an error.", successCounter, errorCounter);
            return recoverResult.getFailedCartList();
        }

        return Collections.emptyList();
    }
}