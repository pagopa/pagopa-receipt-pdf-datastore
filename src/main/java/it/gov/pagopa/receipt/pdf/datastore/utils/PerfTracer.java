package it.gov.pagopa.receipt.pdf.datastore.utils;

import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;

import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.DETAILS_STEP_DURATION_MS;
import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.EVENT_ACTION;
import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.EVENT_OUTCOME;
import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.OUTCOME_FAILURE;
import static it.gov.pagopa.receipt.pdf.datastore.utils.LoggingUtils.OUTCOME_SUCCESS;

/**
 * Lightweight AutoCloseable tracer for I/O boundaries (Cosmos calls, queue sends,
 * HTTP calls). Emits a single ECS-structured INFO/WARN line on {@link #close()}
 * with the step duration and any tag added along the way.
 *
 * <p>Emitted ECS fields:
 * <ul>
 *     <li>{@code event.action} - the logical step name.</li>
 *     <li>{@code event.outcome} - {@code success} or {@code failure}.</li>
 *     <li>{@code ctx.details.step_duration_ms} - StopWatch elapsed time.</li>
 *     <li>Any additional MDC key added via {@link #tag(String, Object)}.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * try (PerfTracer t = PerfTracer.start(logger, "cosmos.saveReceipts")) {
 *     var resp = cosmosClient.saveReceipts(receipt);
 *     t.tag("status_code", resp.getStatusCode())
 *      .tag("request_charge", resp.getRequestCharge());
 * } catch (Exception e) {
 *     // the tracer already logged the failure via close() – rethrow / handle
 *     throw e;
 * }
 * }</pre>
 *
 * <p>Design notes:
 * <ul>
 *     <li>All tags are written to MDC and removed on close so nothing leaks across steps.</li>
 *     <li>Values are pushed as strings (MDC contract). Use it for context/dimensions only;
 *     the only numeric key that reliably keeps its JSON type is
 *     {@code ctx.details.step_duration_ms}, emitted via the fluent API.</li>
 *     <li>For milestone logs with multiple typed metrics use
 *     {@link LoggingUtils} static emitters instead.</li>
 * </ul>
 */
public final class PerfTracer implements AutoCloseable {

    private final Logger logger;
    private final String step;
    private final StopWatch stopWatch;
    private final List<String> tagKeys = new ArrayList<>();

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
     * Adds an MDC field available on the final log line emitted by {@link #close()}.
     * {@code null} values are rendered as {@code "null"}.
     */
    public PerfTracer tag(String key, Object value) {
        MDC.put(key, value == null ? "null" : String.valueOf(value));
        tagKeys.add(key);
        return this;
    }

    /**
     * Marks the step as failed. The final log line will be emitted at WARN level
     * with {@code event.outcome=failure} and the exception details (via ECS encoder).
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

        try {
            String outcome = failed ? OUTCOME_FAILURE : OUTCOME_SUCCESS;
            if (failed) {
                logger.atWarn()
                        .addKeyValue(EVENT_ACTION, step)
                        .addKeyValue(EVENT_OUTCOME, outcome)
                        .addKeyValue(DETAILS_STEP_DURATION_MS, elapsed)
                        .setCause(failure)
                        .log("Step {} failed after {} ms", step, elapsed);
            } else {
                logger.atInfo()
                        .addKeyValue(EVENT_ACTION, step)
                        .addKeyValue(EVENT_OUTCOME, outcome)
                        .addKeyValue(DETAILS_STEP_DURATION_MS, elapsed)
                        .log("Step {} completed in {} ms", step, elapsed);
            }
        } finally {
            for (String k : tagKeys) {
                MDC.remove(k);
            }
            tagKeys.clear();
        }
    }
}

