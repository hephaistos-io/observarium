package io.hephaistos.observarium.posting;

/**
 * The outcome of posting an {@link io.hephaistos.observarium.event.ExceptionEvent} to an external
 * issue tracker via {@link PostingService}.
 *
 * <p>Use the factory methods rather than the record constructor:
 * <ul>
 *   <li>{@link #success(String, String)} — when the issue was created or commented on
 *       successfully.</li>
 *   <li>{@link #failure(String)} — when the posting attempt failed.</li>
 * </ul>
 *
 * <p>Nullability contract:
 * <ul>
 *   <li>{@link #externalIssueId()} and {@link #url()} are {@code null} on failure.</li>
 *   <li>{@link #errorMessage()} is {@code null} on success.</li>
 * </ul>
 */
public record PostingResult(
    boolean success,
    String externalIssueId,
    String url,
    String errorMessage
) {

    /**
     * Returns a successful result carrying the tracker-assigned issue identifier and its URL.
     *
     * @param externalIssueId the identifier assigned by the external tracker, never {@code null}
     * @param url             the URL of the created or updated issue, never {@code null}
     */
    public static PostingResult success(String externalIssueId, String url) {
        return new PostingResult(true, externalIssueId, url, null);
    }

    /**
     * Returns a failure result carrying a human-readable description of what went wrong.
     *
     * @param errorMessage a description of the failure, never {@code null}
     */
    public static PostingResult failure(String errorMessage) {
        return new PostingResult(false, null, null, errorMessage);
    }
}
