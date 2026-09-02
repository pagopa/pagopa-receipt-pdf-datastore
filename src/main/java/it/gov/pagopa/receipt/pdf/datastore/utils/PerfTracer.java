package it.gov.pagopa.receipt.pdf.datastore.utils;

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;

import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.CTX_DETAILS;
import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.DETAILS_STEP_DURATION_MS;
import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.EVENT_ACTION;
import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.EVENT_OUTCOME;
import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.OUTCOME_FAILURE;
import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.OUTCOME_SUCCESS;
import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.detailsAsJson;

/**
 * Lightweight AutoCloseable tracer for I/O boundaries (Cosmos calls, queue sends,
 * HTTP calls). Emits a single ECS-structured INFO/WARN line on {@link #close()}
 * with the step duration and any tag added along the way.
 *
 * <p>Emitted MDC fields (consumed by {@code EcsEncoder}):
 * <ul>
 *     <li>{@code event.action} — the logical step name.</li>
 *     <li>{@code event.outcome} — {@code success} or {@code failure}.</li>
 *     <li>{@code ctx.details} — JSON-serialized map containing
 *         {@code step_duration_ms} and all values added via {@link #tag(String, Object)}.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * try (PerfTracer t = PerfTracer.start(logger, "cosmos.saveReceipts")) {
 *     var resp = cosmosClient.saveReceipts(receipt);
 *     t.tag("status_code", resp.getStatusCode())
 *      .tag("request_charge", resp.getRequestCharge());
 * } catch (Exception e) {
 *     t.markFailure(e);
 *     throw e;
 * }
 * }</pre>
 */
public final class PerfTracer implements AutoCloseable {

    private final Logger logger;
    private final String step;
    private final StopWatch stopWatch;
    private final Map<String, Object> details = new LinkedHashMap<>();

    private boolean failed;
    private Throwable failure;
    private boolean closed;

    private PerfTracer(Logger logger, String step) {
        this.logger = logger;
        this.step = step;
        this.stopWatch = StopWatch.createStarted();
    }

    /** Starts a new tracer measuring the elapsed time until {@link #close()}. */
    public static PerfTracer start(Logger logger, String step) {
        return new PerfTracer(logger, step);
    }

    /**
     * Adds a key/value pair to the {@code ctx.details} map serialized on the final
     * log line. Values keep their JSON type when serialized (numbers stay numbers,
     * booleans stay booleans).
     */
    public PerfTracer tag(String key, Object value) {
        details.put(key, value);
        return this;
    }

    /**
     * Marks the step as failed. The final log line will be emitted at WARN level
     * with {@code event.outcome=failure} and the exception details attached.
     * Idempotent.
     */
    public PerfTracer markFailure(Throwable t) {
        this.failed = true;
        this.failure = t;
        return this;
    }

    /** Elapsed time in milliseconds since the tracer was started. */
    public long elapsedMs() {
        return stopWatch.getTime();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;

        if (!stopWatch.isStopped()) {
            stopWatch.stop();
        }
        long elapsed = stopWatch.getTime();

        // Prepend duration so it stays as the first field in the serialized JSON.
        Map<String, Object> finalDetails = new LinkedHashMap<>();
        finalDetails.put(DETAILS_STEP_DURATION_MS, elapsed);
        finalDetails.putAll(details);

        String outcome = failed ? OUTCOME_FAILURE : OUTCOME_SUCCESS;
        try {
            MDC.put(EVENT_ACTION, step);
            MDC.put(EVENT_OUTCOME, outcome);
            MDC.put(CTX_DETAILS, detailsAsJson(finalDetails));

            if (failed) {
                logger.warn("Step {} failed after {} ms", step, elapsed, failure);
            } else {
                logger.info("Step {} completed in {} ms", step, elapsed);
            }
        } finally {
            MDC.remove(EVENT_ACTION);
            MDC.remove(EVENT_OUTCOME);
            MDC.remove(CTX_DETAILS);
        }
    }
}
