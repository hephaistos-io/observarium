package io.hephaistos.observarium.posting;

import io.hephaistos.observarium.event.ExceptionEvent;

/**
 * Generates standardized issue tracker content from {@link ExceptionEvent} instances.
 *
 * <p>This utility exists so that {@link PostingService} implementations produce consistent
 * titles, bodies, and follow-up comments without duplicating formatting logic. Implementations
 * are expected to delegate to these methods rather than building their own content strings.
 *
 * <p>All methods are stateless and safe to call from multiple threads.
 */
public final class IssueFormatter {

    private IssueFormatter() {}

    /**
     * Produces an HTML comment that embeds a fingerprint into an issue body, enabling
     * fingerprint-based duplicate detection.
     *
     * <p>{@link PostingService} implementations should embed this marker inside the body of
     * every issue they create (via {@link #markdownBody(ExceptionEvent)}) and then search for
     * it by fingerprint value when implementing {@link PostingService#findDuplicate}.
     *
     * @param fingerprint the stable deduplication key from
     *                    {@link ExceptionEvent#fingerprint()}, never {@code null}
     * @return an HTML comment string of the form
     *         {@code <!-- observarium:fingerprint:<fingerprint> -->}
     */
    public static String fingerprintMarker(String fingerprint) {
        return "<!-- observarium:fingerprint:" + fingerprint + " -->";
    }

    /**
     * Generates a concise issue title for the given event, suitable as the title of a new
     * tracker issue.
     *
     * <p>The title uses the unqualified exception class name and up to 80 characters of the
     * exception message. If the event has no message, the placeholder {@code (no message)} is
     * used instead.
     *
     * @param event the exception event to title, never {@code null}
     * @return a non-null, non-empty issue title string
     */
    public static String title(ExceptionEvent event) {
        String shortClass = event.exceptionClass();
        int lastDot = shortClass.lastIndexOf('.');
        if (lastDot >= 0) {
            shortClass = shortClass.substring(lastDot + 1);
        }
        var message = event.message();
        if (message != null && message.length() > 80) {
            message = message.substring(0, 77) + "...";
        }
        return "[Observarium] " + shortClass + ": " + (message != null ? message : "(no message)");
    }

    /**
     * Generates a full markdown body for a newly created issue.
     *
     * <p>The body includes the {@link #fingerprintMarker(String)} as its first line so that
     * future occurrences can be correlated via {@link PostingService#findDuplicate}. It also
     * includes exception details, severity, timestamp, thread name, optional tracing IDs,
     * user-supplied tags, and the full raw stack trace.
     *
     * @param event the exception event to describe, never {@code null}
     * @return a non-null markdown string ready to be submitted as an issue body
     */
    public static String markdownBody(ExceptionEvent event) {
        var body = new StringBuilder();
        body.append(fingerprintMarker(event.fingerprint())).append("\n\n");
        body.append("## Exception\n\n");
        body.append("**Type:** `").append(event.exceptionClass()).append("`\n\n");
        body.append("**Message:** ").append(event.message() != null ? event.message() : "N/A").append("\n\n");
        body.append("**Severity:** ").append(event.severity()).append("\n\n");
        body.append("**Timestamp:** ").append(event.timestamp()).append("\n\n");
        body.append("**Thread:** ").append(event.threadName()).append("\n\n");

        if (event.traceId() != null) {
            body.append("**Trace ID:** `").append(event.traceId()).append("`\n\n");
        }
        if (event.spanId() != null) {
            body.append("**Span ID:** `").append(event.spanId()).append("`\n\n");
        }

        if (!event.tags().isEmpty()) {
            body.append("## Tags\n\n");
            event.tags().forEach((key, value) -> body.append("- **").append(key).append(":** ").append(value).append("\n"));
            body.append("\n");
        }

        body.append("## Stack Trace\n\n```\n");
        body.append(event.rawStackTrace());
        body.append("\n```\n\n");

        body.append("**Fingerprint:** `").append(event.fingerprint()).append("`\n");
        return body.toString();
    }

    /**
     * Generates a markdown comment for a follow-up occurrence of an already-tracked exception.
     *
     * <p>Use this when {@link PostingService#findDuplicate} returns a match: instead of opening
     * a new issue, post this comment on the existing one via
     * {@link PostingService#commentOnIssue}. The comment records the new occurrence's timestamp,
     * thread, optional tracing IDs, tags, and severity without repeating the full stack trace.
     *
     * @param event the new occurrence to document, never {@code null}
     * @return a non-null markdown string ready to be submitted as a tracker comment
     */
    public static String markdownComment(ExceptionEvent event) {
        var comment = new StringBuilder();
        comment.append("## Occurred Again\n\n");
        comment.append("**Timestamp:** ").append(event.timestamp()).append("\n\n");
        comment.append("**Thread:** ").append(event.threadName()).append("\n\n");
        if (event.traceId() != null) {
            comment.append("**Trace ID:** `").append(event.traceId()).append("`\n\n");
        }
        if (!event.tags().isEmpty()) {
            comment.append("**Tags:** ").append(event.tags()).append("\n\n");
        }
        comment.append("**Severity:** ").append(event.severity()).append("\n");
        return comment.toString();
    }
}
