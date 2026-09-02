package it.gov.pagopa.receipt.pdf.datastore.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralized constants and structured emitters for the OER logging guidelines
 * (milestone-driven, ECS field naming, JSON single-line output).
 *
 * <p>All milestone data is emitted through {@link MDC}.
 *
 * <p>Top-level ECS attributes (e.g. {@code event.action}, {@code event.outcome},
 * business ids like {@code biz_event.id}) are set as individual MDC entries.
 * Volatile per-log details live under a single MDC key {@code ctx.details}
 * whose value is a JSON-serialized map.
 *
 * <p>For I/O timing use {@link PerfTracer}.
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

    /** ECS-style business ids. Indexed as top-level MDC fields for cross-service correlation. */
    public static final String BIZ_EVENT_ID = "biz_event.id";
    public static final String CART_ID = "cart.id";
    public static final String EVENT_ID = "event.id";

    /** MDC key that holds the JSON-serialized {@code ctx.details} map. */
    public static final String CTX_DETAILS = "ctx.details";

    // --- ctx.details.* inner keys (volatile fields, serialized inside CTX_DETAILS map) ----------

    // batch summary
    public static final String DETAILS_BATCH_SIZE = "batch_size";
    public static final String DETAILS_PROCESSED = "processed";
    public static final String DETAILS_DISCARDED = "discarded";
    public static final String DETAILS_RECEIPT_FAILED = "receipt_failed";
    public static final String DETAILS_CART_FAILED = "cart_failed";
    public static final String DETAILS_BATCH_DURATION_MS = "batch_duration_ms";
    public static final String DETAILS_TRIGGER_LAG_MIN_MS = "trigger_lag_min_ms";
    public static final String DETAILS_TRIGGER_LAG_AVG_MS = "trigger_lag_avg_ms";
    public static final String DETAILS_TRIGGER_LAG_MAX_MS = "trigger_lag_max_ms";
    public static final String DETAILS_TRIGGER_LAG_P95_MS = "trigger_lag_p95_ms";

    // per-item
    public static final String DETAILS_ITEM_TYPE = "item_type";
    public static final String DETAILS_ITEM_STATUS = "item_status";
    public static final String DETAILS_PROCESSING_MS = "processing_ms";
    public static final String DETAILS_TRIGGER_LAG_MS = "trigger_lag_ms";
    public static final String DETAILS_E2E_LAG_MS = "e2e_lag_ms";

    // I/O step
    public static final String DETAILS_STEP_DURATION_MS = "step_duration_ms";

    // --- event.action values --------------------------------------------------------------------
    public static final String ACTION_BIZ_EVENT_TO_RECEIPT_PROCESSOR = "cosmos-trigger-biz-event-processor";
    public static final String ACTION_BIZ_EVENT_PROCESSING = "biz-event-processing";

    // --- messages -------------------------------------------------------------------------------
    public static final String MSG_BIZ_EVENT_BATCH_PROCESSED = "Biz event batch processed";
    public static final String MSG_BIZ_EVENT_PROCESSED = "Biz event processed";

    // --- I/O step names (used by PerfTracer as event.action on the I/O boundary log) ------------
    public static final String STEP_COSMOS_GET_RECEIPT = "cosmos.getReceipt";
    public static final String STEP_COSMOS_SAVE_RECEIPT = "cosmos.saveReceipt";
    public static final String STEP_COSMOS_UPDATE_RECEIPT = "cosmos.updateReceipt";
    public static final String STEP_COSMOS_GET_CART = "cosmos.getCart";
    public static final String STEP_COSMOS_UPDATE_CART = "cosmos.updateCart";
    public static final String STEP_COSMOS_GET_BIZ_EVENT = "cosmos.getBizEvent";
    public static final String STEP_COSMOS_GET_CART_BIZ_EVENTS = "cosmos.getCartBizEvents";
    public static final String STEP_QUEUE_SEND_RECEIPT = "queue.sendReceipt";
    public static final String STEP_QUEUE_SEND_CART = "queue.sendCart";
    public static final String STEP_PDV_SEARCH_TOKEN = "pdv.searchToken";
    public static final String STEP_PDV_FIND_PII = "pdv.findPii";
    public static final String STEP_PDV_CREATE_TOKEN = "pdv.createToken";

    // --- common tag keys inside ctx.details (used by PerfTracer.tag(...)) ---------------------
    public static final String TAG_STATUS_CODE = "status_code";
    public static final String TAG_FOUND = "found";
    public static final String TAG_FALLBACK = "fallback";
    public static final String TAG_RESULT_COUNT = "result_count";

    /** Classifies the per-item processing path taken by the Function. */
    public enum ReceiptType { SINGLE, CART }

    private static final ObjectMapper JSON = new ObjectMapper();

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

    /**
     * Serializes the given map as a JSON string suitable for the {@code ctx.details}
     * MDC value. {@code null} values are preserved. Never throws: on serialization
     * error falls back to {@code map.toString()} so a logging failure never breaks
     * the caller.
     */
    static String detailsAsJson(Map<String, Object> details) {
        try {
            return JSON.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            return String.valueOf(details);
        }
    }

    // --- milestone emitters ---------------------------------------------------------------------

    /**
     * Emits the batch-summary milestone at the end of a
     * {@code BizEventToReceiptProcessor} invocation. Milestone-driven pattern:
     * single INFO log per invocation, no start/end pair.
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
        Map<String, Object> details = new LinkedHashMap<>();
        details.put(DETAILS_BATCH_SIZE, batchSize);
        details.put(DETAILS_PROCESSED, processed);
        details.put(DETAILS_DISCARDED, discarded);
        details.put(DETAILS_RECEIPT_FAILED, receiptFailed);
        details.put(DETAILS_CART_FAILED, cartFailed);
        details.put(DETAILS_BATCH_DURATION_MS, batchDurationMs);
        details.put(DETAILS_TRIGGER_LAG_MIN_MS, triggerLag.min());
        details.put(DETAILS_TRIGGER_LAG_AVG_MS, triggerLag.avg());
        details.put(DETAILS_TRIGGER_LAG_MAX_MS, triggerLag.max());
        details.put(DETAILS_TRIGGER_LAG_P95_MS, triggerLag.p95());

        Map<String, String> top = new LinkedHashMap<>();
        top.put(EVENT_ACTION, ACTION_BIZ_EVENT_TO_RECEIPT_PROCESSOR);
        top.put(EVENT_OUTCOME, success ? OUTCOME_SUCCESS : OUTCOME_FAILURE);

        emit(logger, MSG_BIZ_EVENT_BATCH_PROCESSED, top, details);
    }

    /**
     * Common emitter: publishes {@code topFields} + {@code ctx.details} on MDC,
     * logs the given message at INFO, and cleans up MDC (leaving pre-existing
     * keys like {@link #CORRELATION_ID} untouched).
     */
    private static void emit(Logger logger, String message, Map<String, String> topFields, Map<String, Object> details) {
        List<String> keysToClear = new ArrayList<>(topFields.size() + 1);
        try {
            for (Map.Entry<String, String> e : topFields.entrySet()) {
                if (e.getValue() != null) {
                    MDC.put(e.getKey(), e.getValue());
                    keysToClear.add(e.getKey());
                }
            }
            MDC.put(CTX_DETAILS, detailsAsJson(details));
            keysToClear.add(CTX_DETAILS);
            logger.info(message);
        } finally {
            for (String k : keysToClear) {
                MDC.remove(k);
            }
        }
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
