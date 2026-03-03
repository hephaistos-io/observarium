/**
 * Integration SPI for posting exception events to external issue trackers.
 *
 * <p>Implement {@link io.hephaistos.observarium.posting.PostingService} to add support for a new
 * backend (e.g. Jira, GitHub Issues, Linear). The interface covers duplicate detection,
 * new-issue creation, and comment-on-existing-issue so that Observarium avoids flooding a
 * tracker with duplicate reports. {@link io.hephaistos.observarium.posting.PostingResult} and
 * {@link io.hephaistos.observarium.posting.DuplicateSearchResult} carry the outcomes back to the
 * pipeline.
 *
 * <p>Issue titles, bodies, and comments are produced by an {@link io.hephaistos.observarium.posting.IssueFormatter}.
 * The {@link io.hephaistos.observarium.posting.DefaultIssueFormatter} ships a sensible default;
 * inject a custom implementation into any {@code PostingService} constructor to control the output format.
 */
package io.hephaistos.observarium.posting;
