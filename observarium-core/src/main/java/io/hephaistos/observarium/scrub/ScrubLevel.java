package io.hephaistos.observarium.scrub;

/**
 * Controls which categories of sensitive data the default {@link DataScrubber} redacts from
 * exception event fields before they are forwarded to a
 * {@link io.hephaistos.observarium.posting.PostingService}.
 *
 * <p>The active level is set via
 * {@link io.hephaistos.observarium.Observarium.Builder#scrubLevel(ScrubLevel)} and defaults
 * to {@link #BASIC}. Each level is a strict superset of the one above it. This setting has no
 * effect when a fully custom {@link DataScrubber} is provided via
 * {@link io.hephaistos.observarium.Observarium.Builder#scrubber(DataScrubber)}.
 */
public enum ScrubLevel {

    /**
     * No automatic redaction is applied.
     *
     * <p>Use only in environments where all data in exception messages and stack frames is
     * known to be non-sensitive (e.g., an isolated local development environment). Not
     * recommended for production or staging.
     */
    NONE,

    /**
     * Redacts common authentication credentials: passwords, API keys, tokens, and
     * Bearer authorization header values.
     *
     * <p>This is the default level. It guards against the most common categories of
     * accidentally leaked secrets without aggressively altering exception messages that
     * contain user-identifying information.
     */
    BASIC,

    /**
     * Redacts everything covered by {@link #BASIC}, plus personally identifiable information
     * (PII): email addresses, IPv4 and IPv6 addresses, and phone numbers.
     *
     * <p>Use in production environments that handle personal data and must comply with
     * privacy regulations (e.g., GDPR, CCPA), where PII must not appear in external
     * issue trackers.
     */
    STRICT
}
