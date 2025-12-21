package it.gov.pagopa.receipt.pdf.datastore.helpdesk.schedule;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.CosmosDBOutput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.TimerTrigger;

import it.gov.pagopa.receipt.pdf.datastore.client.BizEventCosmosClient;
import it.gov.pagopa.receipt.pdf.datastore.client.impl.BizEventCosmosClientImpl;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.Receipt;
import it.gov.pagopa.receipt.pdf.datastore.entity.receipt.enumeration.ReceiptStatusType;
import it.gov.pagopa.receipt.pdf.datastore.model.MassiveRecoverResult;
import it.gov.pagopa.receipt.pdf.datastore.service.BizEventToReceiptService;
import it.gov.pagopa.receipt.pdf.datastore.service.ReceiptCosmosService;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.BizEventToReceiptServiceImpl;
import it.gov.pagopa.receipt.pdf.datastore.service.impl.ReceiptCosmosServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;

import static it.gov.pagopa.receipt.pdf.datastore.utils.BizEventToReceiptUtils.massiveRecoverByStatus;


/**
 * Azure Functions with Timer trigger.
 */
public class RecoverFailedReceiptScheduled {

    private final Logger logger = LoggerFactory.getLogger(RecoverFailedReceiptScheduled.class);

    private final boolean isEnabled = Boolean.parseBoolean(System.getenv().getOrDefault("FAILED_AUTORECOVER_ENABLED", "true"));

    private final BizEventToReceiptService bizEventToReceiptService;
    private final BizEventCosmosClient bizEventCosmosClient;
    private final ReceiptCosmosService receiptCosmosService;

    public RecoverFailedReceiptScheduled() {
        this.bizEventToReceiptService = new BizEventToReceiptServiceImpl();
        this.receiptCosmosService = new ReceiptCosmosServiceImpl();
        this.bizEventCosmosClient = BizEventCosmosClientImpl.getInstance();
    }

    RecoverFailedReceiptScheduled(BizEventToReceiptService bizEventToReceiptService,
                                  BizEventCosmosClient bizEventCosmosClient,
                                  ReceiptCosmosService receiptCosmosService) {
        this.bizEventToReceiptService = bizEventToReceiptService;
        this.bizEventCosmosClient = bizEventCosmosClient;
        this.receiptCosmosService = receiptCosmosService;
    }

    /**
     * This function will be invoked periodically according to the specified schedule.
     * <p>
     * It recovers all the receipts with the following status:
     * - ({@link ReceiptStatusType#INSERTED})
     * - ({@link ReceiptStatusType#FAILED})
     * - ({@link ReceiptStatusType#NOT_QUEUE_SENT})
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
            recover(context, ReceiptStatusType.INSERTED);
            recover(context, ReceiptStatusType.FAILED);
            recover(context, ReceiptStatusType.NOT_QUEUE_SENT);
        }
    }

    private List<Receipt> recover(ExecutionContext context, ReceiptStatusType statusType) {
        try {
            MassiveRecoverResult recoverResult = massiveRecoverByStatus(
                    context, bizEventToReceiptService, bizEventCosmosClient, receiptCosmosService, logger, statusType);
            if (recoverResult.getErrorCounter() > 0) {
                logger.error("[{}] Error recovering {} failed receipts for status {}",
                        context.getFunctionName(), recoverResult.getErrorCounter(), statusType);
            }

            List<String> idList = recoverResult.getReceiptList().parallelStream().map(Receipt::getId).toList();

            logger.info("[{}] Recovered {} receipts for status {} with ids: {}",
                    context.getFunctionName(), idList.size(), statusType, idList);
            return recoverResult.getReceiptList();
        } catch (NoSuchElementException e) {
            logger.error("[{}] Unexpected error during recover of failed receipt for status {}",
                    context.getFunctionName(), statusType, e);
            return Collections.emptyList();
        }
    }
}