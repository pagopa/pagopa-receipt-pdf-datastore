package it.gov.pagopa.receipt.pdf.datastore.utils;
import org.slf4j.Logger;
import org.slf4j.MDC;
import java.util.Arrays;
/**
 * Centralized constants and structured emitters for the OER logging guidelines
 * (milestone-driven, ECS field naming, JSON single-line output).
 *
 * <p>Two categories live here:
 * <ul>
 *     <li>ECS / MDC key constants shared across the whole service (change them here only).</li>
 *     <li>Static milestone emitters using the SLF4J 2 fluent API so that numeric
 *         values keep their JSON type in the {@code EcsEncoder} output
 *         (mandatory for Kibana aggregations / percentiles).</li>
 * </ul>
 *
 * <p>For I/O timing use {@link PerfTracer} instead.
 */
public final class LoggingUtils {
    // --- MDC / ECS top-level keys ---------------------------------------------------------------
    /** ECS field: unique identifier propagated across log lines of the same invocation. */
    public static final String CORRELATION_ID = "correlation.id";
    /** ECS field: logical action/operation being executed. */
    public static final String EVENT_ACTION = "event.action";
    /** ECS field: outcome of the milestone ({@code success} | {@code failure}). */
    public static final String EVENT_OUTCOME = "event.outcome";
    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_FAILURE = "failure";
    // --- ctx.details.* keys (volatile fields, not intended for global indexing) -----------------
    public static final String DETAILS_BATCH_SIZE = "ctx.details.batch_size";
    public static final String DETAILS_PROCESSED = "ctx.details.processed";
    public static final String DETAILS_DISCARDED = "ctx.details.discarded";
    public static final String DETAILS_RECEIPT_FAILED = "ctx.details.receipt_failed";
    public static final String DETAILS_CART_FAILED = "ctx.details.cart_failed";
    public static final String DETAILS_BATCH_DURATION_MS = "ctx.details.batch_duration_ms";
    public static final String DETAILS_TRIGGER_LAG_MIN_MS = "ctx.details.trigger_lag_min_ms";
    public static final String DETAILS_TRIGGER_LAG_AVG_MS = "ctx.details.trigger_lag_avg_ms";
    public static final String DETAILS_TRIGGER_LAG_MAX_MS = "ctx.details.trigger_lag_max_ms";
    public static final String DETAILS_TRIGGER_LAG_P95_MS = "ctx.details.trigger_lag_p95_ms";

    /** Duration of a single I/O step measured by {@link PerfTracer}. */
    public static final String DETAILS_STEP_DURATION_MS = "ctx.details.step_duration_ms";

    // --- per-item milestone keys ---------------------------------------------------------------

    /** ECS-style business ids. Indexed as top-level fields for cross-service correlation. */
    public static final String BIZ_EVENT_ID = "biz_event.id";
    public static final String CART_ID = "cart.id";
    public static final String EVENT_ID = "event.id";

    /** Per-item volatile details. */
    public static final String DETAILS_ITEM_TYPE = "ctx.details.item_type";
    public static final String DETAILS_ITEM_STATUS = "ctx.details.item_status";
    public static final String DETAILS_PROCESSING_MS = "ctx.details.processing_ms";
    public static final String DETAILS_TRIGGER_LAG_MS = "ctx.details.trigger_lag_ms";
    public static final String DETAILS_E2E_LAG_MS = "ctx.details.e2e_lag_ms";

    // --- event.action values --------------------------------------------------------------------
    public static final String ACTION_BIZ_EVENT_TO_RECEIPT_PROCESSOR = "cosmos-trigger-biz-event-processor";
    public static final String ACTION_BIZ_EVENT_PROCESSING = "biz-event-processing";

    // --- messages -------------------------------------------------------------------------------
    public static final String MSG_BIZ_EVENT_BATCH_PROCESSED = "Biz event batch processed";
    public static final String MSG_BIZ_EVENT_PROCESSED = "Biz event processed";

    /** Classifies the per-item processing path taken by the Function. */
    public enum ReceiptType { SINGLE, CART }

    private LoggingUtils() {
        // utility class
    }
    // --- MDC helpers ----------------------------------------------------------------------------
    /** Puts the correlation id in MDC so every log line emitted within the invocation carries it. */
    public static void setCorrelationId(String correlationId) {
        if (correlationId != null) {
            MDC.put(CORRELATION_ID, correlationId);
        }
    }
    /** Removes the correlation id from MDC. To be called in a {@code finally} block. */
    public static void clearCorrelationId() {
        MDC.remove(CORRELATION_ID);
    }
    // --- milestone emitters ---------------------------------------------------------------------
    /**
     * Emits the batch-summary milestone at the end of a
     * {@code BizEventToReceiptProcessor} invocation.
     *
     * <p>Milestone-driven pattern: single INFO log per invocation, no start/end pair.
     * Uses the SLF4J 2 fluent API so counters keep their numeric JSON type
     * on the ELK side (required for Kibana aggregations and percentiles).
     */
    public static void logBizEventBatchProcessed(
            Logger logger,
            int batchSize,
            int processed,
            int discarded,
            int receiptFailed,
            int cartFailed,
            long batchDurationMs,
            TriggerLagStats triggerLag
    ) {
        boolean success = receiptFailed == 0 && cartFailed == 0;
        logger.atInfo()
                .addKeyValue(EVENT_ACTION, ACTION_BIZ_EVENT_TO_RECEIPT_PROCESSOR)
                .addKeyValue(EVENT_OUTCOME, success ? OUTCOME_SUCCESS : OUTCOME_FAILURE)
                .addKeyValue(DETAILS_BATCH_SIZE, batchSize)
                .addKeyValue(DETAILS_PROCESSED, processed)
                .addKeyValue(DETAILS_DISCARDED, discarded)
                .addKeyValue(DETAILS_RECEIPT_FAILED, receiptFailed)
                .addKeyValue(DETAILS_CART_FAILED, cartFailed)
                .addKeyValue(DETAILS_BATCH_DURATION_MS, batchDurationMs)
                .addKeyValue(DETAILS_TRIGGER_LAG_MIN_MS, triggerLag.min())
                .addKeyValue(DETAILS_TRIGGER_LAG_AVG_MS, triggerLag.avg())
                .addKeyValue(DETAILS_TRIGGER_LAG_MAX_MS, triggerLag.max())
                .addKeyValue(DETAILS_TRIGGER_LAG_P95_MS, triggerLag.p95())
                .log(MSG_BIZ_EVENT_BATCH_PROCESSED);
    }

    /**
     * Emits the per-item milestone at the end of a single biz-event processing.
     * Business ids ({@code biz_event.id}, {@code receipt.event_id}
     * or {@code cart.id}) are top-level ECS attributes so they are indexed on ELK and
     * can be searched/correlated across services; timings and status live under
     * {@code ctx.details.*} as volatile fields.
     *
     * @param triggerLagMs delta between Cosmos {@code _ts} and the moment this item
     *                     was picked up by the Function (may be {@code null} if unknown).
     * @param e2eLagMs     delta between Cosmos {@code _ts} and the completion of the
     *                     processing (may be {@code null} if unknown).
     */
    public static void logBizEventProcessed(
            Logger logger,
            String bizEventId,
            String entityId,
            ReceiptType receiptType,
            String receiptStatus,
            boolean success,
            long processingMs,
            Long triggerLagMs,
            Long e2eLagMs
    ) {
        String entityKey = receiptType == ReceiptType.CART ? CART_ID : EVENT_ID;
        logger.atInfo()
                .addKeyValue(EVENT_ACTION, ACTION_BIZ_EVENT_PROCESSING)
                .addKeyValue(EVENT_OUTCOME, success ? OUTCOME_SUCCESS : OUTCOME_FAILURE)
                .addKeyValue(BIZ_EVENT_ID, bizEventId)
                .addKeyValue(entityKey, entityId)
                .addKeyValue(DETAILS_ITEM_TYPE, receiptType.name())
                .addKeyValue(DETAILS_ITEM_STATUS, receiptStatus)
                .addKeyValue(DETAILS_PROCESSING_MS, processingMs)
                .addKeyValue(DETAILS_TRIGGER_LAG_MS, triggerLagMs)
                .addKeyValue(DETAILS_E2E_LAG_MS, e2eLagMs)
                .log(MSG_BIZ_EVENT_PROCESSED);
    }

    // --- support data structures ---------------------------------------------------------------
    /**
     * Fixed-capacity accumulator for the Cosmos change-feed trigger lag
     * (delta between {@code _ts} of the biz-event and the moment the Function
     * receives it), used to build the summary milestone.
     * A bounded array keeps memory predictable across invocations.
     */
    public static final class TriggerLagStats {
        private static final int CAPACITY = 128;
        private final long[] lags = new long[CAPACITY];
        private int count;
        /**
         * Tracks the lag for a single biz-event given its Cosmos {@code _ts} in epoch seconds.
         * {@code null} timestamps and future timestamps (negative lag) are ignored.
         */
        public void track(Long bizEventTs) {
            if (bizEventTs == null) return;
            long lagMs = System.currentTimeMillis() - (bizEventTs * 1000L);
            if (lagMs < 0) return;
            if (count < CAPACITY) {
                lags[count++] = lagMs;
            }
        }
        public Long min() {
            if (count == 0) return null;
            long v = Long.MAX_VALUE;
            for (int i = 0; i < count; i++) if (lags[i] < v) v = lags[i];
            return v;
        }
        public Long max() {
            if (count == 0) return null;
            long v = Long.MIN_VALUE;
            for (int i = 0; i < count; i++) if (lags[i] > v) v = lags[i];
            return v;
        }
        public Long avg() {
            if (count == 0) return null;
            long sum = 0;
            for (int i = 0; i < count; i++) sum += lags[i];
            return sum / count;
        }
        public Long p95() {
            if (count == 0) return null;
            long[] sorted = Arrays.copyOf(lags, count);
            Arrays.sort(sorted);
            int idx = (int) Math.ceil(0.95 * count) - 1;
            if (idx < 0) idx = 0;
            return sorted[idx];
        }
    }
}
