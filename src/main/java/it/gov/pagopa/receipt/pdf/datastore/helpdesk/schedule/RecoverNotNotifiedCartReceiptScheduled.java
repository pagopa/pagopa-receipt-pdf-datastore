package it.gov.pagopa.receipt.pdf.datastore.helpdesk.schedule;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartForReceipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.cart.CartStatusType;
import it.gov.pagopa.receipt.pdf.datastore.service.HelpdeskService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.HelpdeskServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class RecoverNotNotifiedCartReceiptScheduled {

    private final boolean isEnabled = Boolean.parseBoolean(System.getenv().getOrDefault("NOT_NOTIFIED_CART_AUTORECOVER_ENABLED", "true"));

    private final Logger logger = LoggerFactory.getLogger(RecoverNotNotifiedCartReceiptScheduled.class);

    private final HelpdeskService helpdeskService;

    public RecoverNotNotifiedCartReceiptScheduled() {
        this.helpdeskService = new HelpdeskServiceImpl();
    }

    RecoverNotNotifiedCartReceiptScheduled(HelpdeskService helpdeskService) {
        this.helpdeskService = helpdeskService;
    }

    /**
     * This function will be invoked on a scheduled basis.
     * <p>
     * It recovers all cart receipt with the provided status.
     * <p>
     * It recovers the cart receipt with failed notification ({@link CartStatusType#IO_ERROR_TO_NOTIFY}) or notification
     * not triggered ({@link CartStatusType#GENERATED} by clearing the errors and update the status to the
     * previous step ({@link CartStatusType#GENERATED}).
     */
    @FunctionName("RecoverNotNotifiedCartTimerTriggerProcessor")
    public void processRecoverNotNotifiedScheduledTrigger(
            @TimerTrigger(
                    name = "timerInfoNotNotifiedCart",
                    schedule = "%RECOVER_CART_FAILED_CRON%"
            )
            String timerInfo,
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

            List<CartForReceipt> receiptList = new ArrayList<>();
            receiptList.addAll(process(context, CartStatusType.IO_ERROR_TO_NOTIFY));
            receiptList.addAll(process(context, CartStatusType.GENERATED));

            documentdb.setValue(receiptList);
        }
    }

    private List<CartForReceipt> process(ExecutionContext context, CartStatusType statusType) {
        List<CartForReceipt> receiptList = this.helpdeskService.massiveRecoverNoNotifiedCart(statusType);

        List<String> idList = receiptList.parallelStream().map(CartForReceipt::getId).toList();
        logger.info("[{}] Recovered {} cart receipts for status {} with ids: {}",
                context.getFunctionName(), receiptList.size(), statusType, idList);
        return receiptList;
    }
}
