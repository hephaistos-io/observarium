package io.hephaistos.observarium.posting;

import io.hephaistos.observarium.event.ExceptionEvent;

/**
 * Primary extension point for integrating Observarium with an external issue tracker.
 *
 * <p>Implement this interface to connect Observarium to any backend — GitHub Issues, Jira, GitLab,
 * Linear, or a custom system. Observarium drives a fixed lifecycle per captured exception:
 *
 * <ol>
 *   <li>{@link #findDuplicate(ExceptionEvent)} is called first. If an existing issue is found,
 *       Observarium calls {@link #commentOnIssue(String, ExceptionEvent)} with its ID.
 *   <li>If no duplicate is found, {@link #createIssue(ExceptionEvent)} is called to open a new
 *       issue.
 * </ol>
 *
 * <p>To reliably detect duplicates across restarts, embed the event's fingerprint in the issue body
 * when creating it (see {@link IssueFormatter#fingerprintMarker(String)}), then scan for that
 * marker in {@code findDuplicate}.
 *
 * <p><b>Thread-safety:</b> all three lifecycle methods are invoked from Observarium's internal
 * worker thread. Implementations must be thread-safe if shared state is involved, but do not need
 * to handle concurrent calls to the same instance.
 */
public interface PostingService {

  /**
   * Returns a human-readable name for this posting service, used in logs and diagnostics.
   *
   * @return non-null, non-empty name identifying this integration (e.g. {@code "github"})
   */
  String name();

  /**
   * Searches the external tracker for an issue that was previously created for the same logical
   * exception as {@code event}.
   *
   * <p>Implementations should use {@code event.fingerprint()} as the lookup key. A common approach
   * is to search issues whose body contains {@link IssueFormatter#fingerprintMarker(String)
   * IssueFormatter.fingerprintMarker(event.fingerprint())}.
   *
   * @param event the fully-built, scrubbed event to search for; never null
   * @return {@link DuplicateSearchResult#found(String, String)} if a matching issue exists, {@link
   *     DuplicateSearchResult#notFound()} otherwise; never null
   */
  DuplicateSearchResult findDuplicate(ExceptionEvent event);

  /**
   * Creates a new issue in the external tracker for the given event.
   *
   * <p>Called only when {@link #findDuplicate(ExceptionEvent)} returned no match. Implementations
   * should embed {@link IssueFormatter#fingerprintMarker(String)} in the issue body so future
   * occurrences can be detected as duplicates.
   *
   * @param event the event to report; never null
   * @return a {@link PostingResult} indicating success or failure; never null
   */
  PostingResult createIssue(ExceptionEvent event);

  /**
   * Adds a comment to an existing issue recording a new occurrence of the same exception.
   *
   * <p>Called when {@link #findDuplicate(ExceptionEvent)} returned a match. Use {@link
   * IssueFormatter#markdownComment(ExceptionEvent)} to produce a consistent comment body.
   *
   * @param externalIssueId the tracker-specific issue identifier returned by {@link
   *     DuplicateSearchResult#externalIssueId()}; never null
   * @param event the new occurrence to record; never null
   * @return a {@link PostingResult} indicating success or failure; never null
   */
  PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event);

  /**
   * Derives a label from the event fingerprint for tagging issues in external trackers.
   *
   * <p>The label uses the first 12 characters of the fingerprint hash prefixed with {@code
   * "observarium-"}, e.g. fingerprint {@code "abc123def456xyz"} produces label {@code
   * "observarium-abc123def456"}.
   *
   * @param fingerprint the event fingerprint; never null
   * @return the label string; never null
   */
  default String fingerprintLabel(String fingerprint) {
    String hash = fingerprint.length() > 12 ? fingerprint.substring(0, 12) : fingerprint;
    return "observarium-" + hash;
  }
}
