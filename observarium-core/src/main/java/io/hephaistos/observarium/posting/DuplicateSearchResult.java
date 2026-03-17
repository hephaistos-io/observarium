package io.hephaistos.observarium.posting;

/**
 * The outcome of searching an external issue tracker for an existing issue that matches an
 * exception's fingerprint, as returned by {@link PostingService#findDuplicate}.
 *
 * <p>Use the factory methods rather than the record constructor:
 *
 * <ul>
 *   <li>{@link #notFound()} — when no matching issue exists in the tracker.
 *   <li>{@link #found(String, String)} — when a matching issue was located but the comment count is
 *       unknown.
 *   <li>{@link #found(String, String, int)} — when a matching issue was located and the service
 *       provides the current comment count.
 * </ul>
 *
 * <p>Nullability contract: when {@link #found()} is {@code false}, both {@link #externalIssueId()}
 * and {@link #url()} are {@code null}.
 *
 * @param found {@code true} if a matching issue was located
 * @param externalIssueId the tracker-specific issue identifier, or {@code null} when not found
 * @param url the web URL of the issue, or {@code null} when not found
 * @param commentCount the number of comments on the issue, or {@link #COMMENT_COUNT_UNKNOWN} when
 *     the service did not provide a count
 */
public record DuplicateSearchResult(
    boolean found, String externalIssueId, String url, int commentCount) {

  /** Sentinel indicating the comment count is unknown (service did not provide it). */
  public static final int COMMENT_COUNT_UNKNOWN = -1;

  public static DuplicateSearchResult notFound() {
    return new DuplicateSearchResult(false, null, null, COMMENT_COUNT_UNKNOWN);
  }

  /** Creates a result when a matching issue was located but the comment count is unknown. */
  public static DuplicateSearchResult found(String externalIssueId, String url) {
    return new DuplicateSearchResult(true, externalIssueId, url, COMMENT_COUNT_UNKNOWN);
  }

  /** Creates a result when a matching issue was located with a known comment count. */
  public static DuplicateSearchResult found(String externalIssueId, String url, int commentCount) {
    if (commentCount < COMMENT_COUNT_UNKNOWN) {
      throw new IllegalArgumentException(
          "commentCount must be >= -1 (COMMENT_COUNT_UNKNOWN), got: " + commentCount);
    }
    return new DuplicateSearchResult(true, externalIssueId, url, commentCount);
  }
}
