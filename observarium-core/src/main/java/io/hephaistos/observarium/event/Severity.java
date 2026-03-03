package io.hephaistos.observarium.event;

/**
 * Indicates the operational impact of a captured exception.
 *
 * <p>Severity is passed to
 * {@link io.hephaistos.observarium.Observarium#captureException(Throwable, Severity)} and
 * is included in the normalized {@link ExceptionEvent} that each
 * {@link io.hephaistos.observarium.posting.PostingService} receives. Posting service
 * implementations may use it to set issue priority or labels on the external tracker.
 */
public enum Severity {

    /**
     * An exception that was handled and did not disrupt the current operation.
     *
     * <p>Use for expected error conditions that are caught and recovered from, such as a
     * failed validation or a transient downstream timeout that triggered a retry.
     */
    INFO,

    /**
     * An exception that signals a degraded or unexpected condition worth investigating, but
     * that did not cause a user-visible failure.
     *
     * <p>Use when an operation succeeded despite encountering an anomalous state, or when a
     * fallback path was taken.
     */
    WARNING,

    /**
     * An unrecoverable exception that caused a discrete operation or request to fail.
     *
     * <p>This is the default severity used by
     * {@link io.hephaistos.observarium.Observarium#captureException(Throwable)}. Use for
     * exceptions that are surfaced to the end user as an error or that cause a transaction
     * rollback.
     */
    ERROR,

    /**
     * An exception that caused or is about to cause the process to terminate.
     *
     * <p>Use for unhandled exceptions caught by the JVM's uncaught exception handler (e.g.,
     * via {@link io.hephaistos.observarium.handler.ObservariumExceptionHandler}). When
     * {@code FATAL} is used in that context, the uncaught exception handler blocks for up to
     * five seconds to ensure the report is delivered before the JVM exits.
     */
    FATAL
}
