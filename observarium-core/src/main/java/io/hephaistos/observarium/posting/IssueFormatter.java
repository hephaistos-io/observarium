package io.hephaistos.observarium.posting;

import io.hephaistos.observarium.event.ExceptionEvent;

/**
 * Generates issue tracker content from {@link ExceptionEvent} instances.
 *
 * <p>{@link PostingService} implementations delegate to an {@code IssueFormatter} to produce
 * titles, bodies, and follow-up comments. The default implementation is {@link
 * DefaultIssueFormatter}. Provide a custom implementation to change formatting — for example, to
 * use a different title convention, to include additional fields, or to produce HTML instead of
 * markdown.
 *
 * <p>Implementations must be thread-safe.
 *
 * @see DefaultIssueFormatter
 */
public interface IssueFormatter {

  /**
   * Produces a marker string that embeds a fingerprint into an issue body, enabling
   * fingerprint-based duplicate detection.
   *
   * <p>{@link PostingService} implementations should embed this marker inside the body of every
   * issue they create and then search for it by fingerprint value when implementing {@link
   * PostingService#findDuplicate}.
   *
   * @param fingerprint the stable deduplication key from {@link ExceptionEvent#fingerprint()},
   *     never {@code null}
   * @return a string that uniquely identifies the fingerprint within an issue body
   */
  String fingerprintMarker(String fingerprint);

  /**
   * Generates a concise issue title for the given event.
   *
   * @param event the exception event to title, never {@code null}
   * @return a non-null, non-empty issue title string
   */
  String title(ExceptionEvent event);

  /**
   * Generates a full body for a newly created issue.
   *
   * <p>The body should include the {@link #fingerprintMarker(String)} so that future occurrences
   * can be correlated via {@link PostingService#findDuplicate}.
   *
   * @param event the exception event to describe, never {@code null}
   * @return a non-null string ready to be submitted as an issue body
   */
  String markdownBody(ExceptionEvent event);

  /**
   * Generates a comment for a follow-up occurrence of an already-tracked exception.
   *
   * <p>Use this when {@link PostingService#findDuplicate} returns a match: instead of opening a new
   * issue, post this comment on the existing one via {@link PostingService#commentOnIssue}.
   *
   * @param event the new occurrence to document, never {@code null}
   * @return a non-null string ready to be submitted as a tracker comment
   */
  String markdownComment(ExceptionEvent event);
}
