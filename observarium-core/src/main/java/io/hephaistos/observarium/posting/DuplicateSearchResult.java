package io.hephaistos.observarium.posting;

/**
 * The outcome of searching an external issue tracker for an existing issue that matches an
 * exception's fingerprint, as returned by {@link PostingService#findDuplicate}.
 *
 * <p>Use the factory methods rather than the record constructor:
 * <ul>
 *   <li>{@link #notFound()} — when no matching issue exists in the tracker.</li>
 *   <li>{@link #found(String, String)} — when a matching issue was located.</li>
 * </ul>
 *
 * <p>Nullability contract: when {@link #found()} is {@code false}, both
 * {@link #externalIssueId()} and {@link #url()} are {@code null}.
 */
public record DuplicateSearchResult(
    boolean found,
    String externalIssueId,
    String url
) {

    /**
     * Returns a result indicating that no matching issue was found in the external tracker.
     */
    public static DuplicateSearchResult notFound() {
        return new DuplicateSearchResult(false, null, null);
    }

    /**
     * Returns a result indicating that a matching issue was found.
     *
     * @param externalIssueId the identifier of the existing issue in the external tracker,
     *                        never {@code null}
     * @param url             the URL of the existing issue, never {@code null}
     */
    public static DuplicateSearchResult found(String externalIssueId, String url) {
        return new DuplicateSearchResult(true, externalIssueId, url);
    }
}
