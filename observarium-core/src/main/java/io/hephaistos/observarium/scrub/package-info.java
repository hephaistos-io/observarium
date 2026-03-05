/**
 * Sensitive data scrubbing applied to exception messages and stack traces before external posting.
 *
 * <p>{@link io.hephaistos.observarium.scrub.DataScrubber} defines the SPI; implement it to plug in
 * custom redaction logic. {@link io.hephaistos.observarium.scrub.DefaultDataScrubber} provides
 * pattern-based redaction controlled by {@link io.hephaistos.observarium.scrub.ScrubLevel}, which
 * offers preset aggressiveness levels ranging from basic credential removal to aggressive PII
 * scrubbing.
 */
package io.hephaistos.observarium.scrub;
