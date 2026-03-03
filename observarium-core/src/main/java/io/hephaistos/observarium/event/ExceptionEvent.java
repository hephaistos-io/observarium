package io.hephaistos.observarium.event;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The normalized, enriched representation of a captured exception as it flows through the
 * observarium pipeline.
 *
 * <p>An {@code ExceptionEvent} is produced after a raw {@link Throwable} has been enriched with
 * system context (thread name, timestamp, tracing IDs) and assigned a stable deduplication
 * {@link #fingerprint()} by {@link io.hephaistos.observarium.fingerprint.ExceptionFingerprinter}.
 * It is the primary input to {@link io.hephaistos.observarium.posting.PostingService}
 * implementations.
 *
 * <p>Field distinctions worth noting:
 * <ul>
 *   <li>{@link #tags()} — caller-supplied metadata passed directly to
 *       {@code Observarium.captureException()}, such as environment or user context.</li>
 *   <li>{@link #extra()} — system-collected metadata added automatically by the pipeline,
 *       such as thread name.</li>
 *   <li>{@link #stackTrace()} — structured list of individual stack frame strings.</li>
 *   <li>{@link #rawStackTrace()} — the full printed trace as produced by
 *       {@link Throwable#printStackTrace()}, including cause chains.</li>
 *   <li>{@link #message()} — the exception message; may be {@code null} if the exception
 *       was constructed without one.</li>
 *   <li>{@link #traceId()} / {@link #spanId()} — distributed tracing context; both are
 *       {@code null} when no tracing context is available.</li>
 * </ul>
 *
 * <p>Construct instances via {@link #builder()}.
 *
 * <p>This record is immutable and safe to share across threads.
 */
public record ExceptionEvent(
    String fingerprint,
    String exceptionClass,
    String message,
    List<String> stackTrace,
    String rawStackTrace,
    Severity severity,
    Instant timestamp,
    String threadName,
    String traceId,
    String spanId,
    Map<String, String> tags,
    Map<String, String> extra
) {

    /**
     * Returns a new {@link Builder} for constructing an {@code ExceptionEvent}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link ExceptionEvent}.
     *
     * <p>Defaults applied when not explicitly set:
     * <ul>
     *   <li>{@code severity} — {@link Severity#ERROR}</li>
     *   <li>{@code timestamp} — {@code Instant.now()} at the time {@link #build()} is called</li>
     *   <li>{@code stackTrace} — empty list</li>
     *   <li>{@code tags} — empty map</li>
     *   <li>{@code extra} — empty map</li>
     * </ul>
     */
    public static final class Builder {
        private String fingerprint;
        private String exceptionClass;
        private String message;
        private List<String> stackTrace = List.of();
        private String rawStackTrace;
        private Severity severity = Severity.ERROR;
        private Instant timestamp = Instant.now();
        private String threadName;
        private String traceId;
        private String spanId;
        private Map<String, String> tags = Map.of();
        private Map<String, String> extra = Map.of();

        /**
         * Sets the stable deduplication key computed by
         * {@link io.hephaistos.observarium.fingerprint.ExceptionFingerprinter}.
         */
        public Builder fingerprint(String fingerprint) { this.fingerprint = fingerprint; return this; }

        /** Sets the fully-qualified exception class name. */
        public Builder exceptionClass(String exceptionClass) { this.exceptionClass = exceptionClass; return this; }

        /**
         * Sets the exception message. May be {@code null} when the exception has no message.
         */
        public Builder message(String message) { this.message = message; return this; }

        /**
         * Sets the structured stack frames. The provided list is copied defensively.
         */
        public Builder stackTrace(List<String> stackTrace) { this.stackTrace = List.copyOf(stackTrace); return this; }

        /**
         * Sets the full printed stack trace including cause chains, as produced by
         * {@link Throwable#printStackTrace()}.
         */
        public Builder rawStackTrace(String rawStackTrace) { this.rawStackTrace = rawStackTrace; return this; }

        /** Sets the severity. Defaults to {@link Severity#ERROR} if not called. */
        public Builder severity(Severity severity) { this.severity = severity; return this; }

        /** Sets the time at which the exception was captured. */
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }

        /** Sets the name of the thread that captured the exception. */
        public Builder threadName(String threadName) { this.threadName = threadName; return this; }

        /**
         * Sets the distributed trace ID. Pass {@code null} when no tracing context is
         * available.
         */
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }

        /**
         * Sets the distributed span ID. Pass {@code null} when no tracing context is
         * available.
         */
        public Builder spanId(String spanId) { this.spanId = spanId; return this; }

        /**
         * Sets caller-supplied metadata tags. The provided map is copied defensively.
         */
        public Builder tags(Map<String, String> tags) { this.tags = Map.copyOf(tags); return this; }

        /**
         * Sets system-collected metadata. The provided map is copied defensively.
         */
        public Builder extra(Map<String, String> extra) { this.extra = Map.copyOf(extra); return this; }

        /** Builds and returns the {@link ExceptionEvent}. */
        public ExceptionEvent build() {
            return new ExceptionEvent(
                fingerprint, exceptionClass, message, stackTrace,
                rawStackTrace, severity, timestamp, threadName,
                traceId, spanId, tags, extra
            );
        }
    }
}
