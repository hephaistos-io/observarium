package io.hephaistos.observarium.posting;

/**
 * The outcome of posting an {@link io.hephaistos.observarium.event.ExceptionEvent} to an external
 * issue tracker via {@link PostingService}.
 *
 * <p>Use the factory methods rather than the record constructor:
 *
 * <ul>
 *   <li>{@link #success(String, String)} — when the issue was created or commented on successfully.
 *   <li>{@link #failure(String)} — when the posting attempt failed.
 * </ul>
 *
 * <p>Nullability contract:
 *
 * <ul>
 *   <li>{@link #externalIssueId()} and {@link #url()} are {@code null} on failure.
 *   <li>{@link #errorMessage()} is {@code null} on success.
 * </ul>
 */
public record PostingResult(
    boolean success, String externalIssueId, String url, String errorMessage) {

  public static PostingResult success(String externalIssueId, String url) {
    return new PostingResult(true, externalIssueId, url, null);
  }

  public static PostingResult failure(String errorMessage) {
    return new PostingResult(false, null, null, errorMessage);
  }
}
