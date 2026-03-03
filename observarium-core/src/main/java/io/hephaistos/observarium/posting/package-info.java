/**
 * Integration SPI for posting exception events to external issue trackers.
 *
 * <p>Implement {@link io.hephaistos.observarium.posting.PostingService} to add support for a new
 * backend (e.g. Jira, GitHub Issues, Linear). The interface covers duplicate detection,
 * new-issue creation, and comment-on-existing-issue so that Observarium avoids flooding a
 * tracker with duplicate reports. {@link io.hephaistos.observarium.posting.PostingResult} and
 * {@link io.hephaistos.observarium.posting.DuplicateSearchResult} carry the outcomes back to the
 * pipeline.
 */
package io.hephaistos.observarium.posting;
