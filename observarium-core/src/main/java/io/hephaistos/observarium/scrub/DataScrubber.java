package io.hephaistos.observarium.scrub;

/**
 * SPI for redacting sensitive data from exception content before it is posted to an external
 * issue tracker.
 *
 * <p>Observarium passes exception messages and stack trace text through the registered scrubber
 * prior to building an {@link io.hephaistos.observarium.event.ExceptionEvent}. This gives
 * applications a single interception point to prevent credentials, PII, or other confidential
 * values from leaking into issue trackers.
 *
 * <p>Provide a custom implementation when you need scrubbing logic that goes beyond regex
 * substitution — for example, calling a secrets-detection service or applying domain-specific
 * masking rules.
 */
public interface DataScrubber {

    /**
     * Returns a scrubbed copy of the given text with sensitive content replaced or removed.
     *
     * <p>Implementations must handle a {@code null} input gracefully and return {@code null}
     * in that case, preserving the null-pass-through contract so callers do not need null guards.
     *
     * @param text the raw string to scrub, may be null
     * @return the scrubbed string, or null if {@code text} was null
     */
    String scrub(String text);
}
