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
 */
public final class LoggingUtils {

    // --- MDC / ECS top-level keys ---------------------------------------------------------------

    public static final String CORRELATION_ID = "correlation.id";
    public static final String EVENT_ACTION = "event.action";
    public static final String EVENT_OUTCOME = "event.outcome";
    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_FAILURE = "failure";

    public static final String BIZ_EVENT_ID = "biz_event.id";
    public static final String CART_ID = "cart.id";
    public static final String EVENT_ID = "event.id";


    /** MDC key that holds the JSON-serialized {@code ctx.details} map. */
    public static final String CTX_DETAILS = "ctx.details";

    // --- ctx.details inner keys -----------------------------------------------------------------

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

    // I/O common
    public static final String DETAILS_DEPENDENCY = "dependency";
    public static final String DETAILS_PATH = "path";
    public static final String DETAILS_DURATION_MS = "duration_ms";
    public static final String DETAILS_STATUS_CODE = "status_code";
    public static final String DETAILS_FOUND = "found";
    public static final String DETAILS_FALLBACK = "fallback";
    public static final String DETAILS_RESULT_COUNT = "result_count";

    // --- event.action values --------------------------------------------------------------------
    public static final String ACTION_BIZ_EVENT_TO_RECEIPT_PROCESSOR = "cosmos-trigger-biz-event-processor";

    // --- messages (static, past tense) ----------------------------------------------------------
    public static final String MSG_BIZ_EVENT_BATCH_PROCESSED = "Biz event batch processed";
    public static final String MSG_FETCHED_RECEIPT = "Fetched receipt";
    public static final String MSG_PDV_SEARCHED_TOKEN = "Searched PDV token";
    public static final String MSG_PDV_FETCHED_PII = "Fetched PII by token";
    public static final String MSG_PDV_CREATED_TOKEN = "Created PDV token";

    // --- dependency logical names (ctx.details.dependency) --------------------------------------
    public static final String DEP_COSMOS_RECEIPTS = "cosmos-receipts";
    public static final String DEP_COSMOS_CARTS = "cosmos-carts";
    public static final String DEP_COSMOS_BIZ_EVENTS = "cosmos-biz-events";
    public static final String DEP_QUEUE_RECEIPTS = "storage-queue-receipts";
    public static final String DEP_QUEUE_CARTS = "storage-queue-carts";
    public static final String DEP_PDV_TOKENIZER = "pdv-tokenizer";

    // --- endpoint / container paths (ctx.details.path) ------------------------------------------
    public static final String PATH_PDV_SEARCH_TOKEN = "/tokens/search";
    public static final String PATH_PDV_FIND_PII = "/tokens/{token}/pii";
    public static final String PATH_PDV_CREATE_TOKEN = "/tokens";

    private static final ObjectMapper JSON = new ObjectMapper();

    private LoggingUtils() {
        // utility class
    }

    // --- invocation-scoped MDC helpers ----------------------------------------------------------

    /**
     * Puts {@link #CORRELATION_ID} and {@link #EVENT_ACTION} in MDC so every log
     * emitted within the invocation (including nested I/O milestones) carries them.
     * Must be paired with {@link #clearInvocation()} in a {@code finally} block.
     */
    public static void initInvocation(String correlationId, String action) {
        if (correlationId != null) {
            MDC.put(CORRELATION_ID, correlationId);
        }
        if (action != null) {
            MDC.put(EVENT_ACTION, action);
        }
    }

    /** Removes the invocation-scoped MDC keys. */
    public static void clearInvocation() {
        MDC.remove(CORRELATION_ID);
        MDC.remove(EVENT_ACTION);
    }

    /**
     * Convenience factory for a single business-id top-level map that tolerates
     * a {@code null} value (returns an empty map). Intended to be passed to the
     * I/O emitters as the {@code businessIds} argument.
     */
    public static Map<String, String> ids(String key, String value) {
        return value == null ? Map.of() : Map.of(key, value);
    }

    // --- milestone emitters ---------------------------------------------------------------------

    /**
     * Emits the batch-summary milestone at the end of a
     * {@code BizEventToReceiptProcessor} invocation. Single INFO log per invocation.
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
        top.put(EVENT_OUTCOME, success ? OUTCOME_SUCCESS : OUTCOME_FAILURE);

        emit(logger, MSG_BIZ_EVENT_BATCH_PROCESSED, top, details, null);
    }

    /**
     * Milestone for a successful I/O interaction. Emits a single INFO log with
     * {@code event.outcome=success}, dependency, path and duration.
     *
     * @param message      static message describing the completed action (past tense).
     * @param dependency   logical name of the downstream system (e.g. {@code cosmos-receipts}).
     * @param path         endpoint / container / queue identifier.
     * @param startMs      value returned by {@link System#currentTimeMillis()} before the I/O.
     * @param extraDetails optional per-call fields merged into {@code ctx.details} (nullable).
     */
    public static void logIoSuccess(
            Logger logger,
            String message,
            String dependency,
            String path,
            long startMs,
            Map<String, Object> extraDetails
    ) {
        logIoSuccess(logger, message, dependency, path, startMs, null, extraDetails);
    }

    /**
     * Variant of {@link #logIoSuccess(Logger, String, String, String, long, Map)} that
     * accepts a map of business ids (e.g. {@code biz_event.id}, {@code cart.id})
     * to be exposed as top-level ECS fields (indexed for cross-service correlation).
     */
    public static void logIoSuccess(
            Logger logger,
            String message,
            String dependency,
            String path,
            long startMs,
            Map<String, String> businessIds,
            Map<String, Object> extraDetails
    ) {
        Map<String, Object> details = ioDetails(dependency, path, System.currentTimeMillis() - startMs, extraDetails);
        Map<String, String> top = new LinkedHashMap<>();
        top.put(EVENT_OUTCOME, OUTCOME_SUCCESS);
        if (businessIds != null) top.putAll(businessIds);
        emit(logger, message, top, details, null);
    }

    /**
     * Milestone for a failed I/O interaction. Emits a single ERROR log with
     * {@code event.outcome=failure}, {@code error.type}, {@code error.message}
     * and stack trace populated by the ECS encoder from {@code error}.
     */
    public static void logIoFailure(
            Logger logger,
            String message,
            String dependency,
            String path,
            long startMs,
            Throwable error,
            Map<String, Object> extraDetails
    ) {
        logIoFailure(logger, message, dependency, path, startMs, error, null, extraDetails);
    }

    /**
     * Variant of {@link #logIoFailure(Logger, String, String, String, long, Throwable, Map)}
     * that accepts business ids as top-level ECS fields.
     */
    public static void logIoFailure(
            Logger logger,
            String message,
            String dependency,
            String path,
            Long startMs,
            Throwable error,
            Map<String, String> businessIds,
            Map<String, Object> extraDetails
    ) {
        Map<String, Object> details = ioDetails(dependency, path, startMs, extraDetails);
        Map<String, String> top = new LinkedHashMap<>();
        top.put(EVENT_OUTCOME, OUTCOME_FAILURE);
        if (businessIds != null) top.putAll(businessIds);
        emit(logger, message, top, details, error);
    }

    // --- internals ------------------------------------------------------------------------------

    private static Map<String, Object> ioDetails(String dependency, String path, Long startMs, Map<String, Object> extra) {
        Map<String, Object> d = new LinkedHashMap<>();
        if (dependency != null) d.put(DETAILS_DEPENDENCY, dependency);
        if (path != null) d.put(DETAILS_PATH, path);
        if (startMs != null) d.put(DETAILS_DURATION_MS, System.currentTimeMillis() - startMs);
        if (extra != null) d.putAll(extra);
        return d;
    }

    /**
     * Serializes the given map as a JSON string suitable for the {@code ctx.details}
     * MDC value. Never throws: on serialization error falls back to
     * {@code map.toString()}.
     */
    static String detailsAsJson(Map<String, Object> details) {
        try {
            return JSON.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            return String.valueOf(details);
        }
    }

    /**
     * Common emitter: publishes {@code topFields} + {@code ctx.details} on MDC,
     * logs the given message and cleans up MDC (leaving invocation-scoped keys
     * untouched). Uses INFO when {@code error == null}, ERROR otherwise.
     */
    private static void emit(
            Logger logger,
            String message,
            Map<String, String> topFields,
            Map<String, Object> details,
            Throwable error
    ) {
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
            if (error != null) {
                logger.error(message, error);
            } else {
                logger.info(message);
            }
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
     * receives it).
     */
    public static final class TriggerLagStats {
        private static final int CAPACITY = 128;
        private final long[] lags = new long[CAPACITY];
        private int count;

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
