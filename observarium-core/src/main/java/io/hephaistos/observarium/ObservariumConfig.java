package io.hephaistos.observarium;

import io.hephaistos.observarium.scrub.ScrubLevel;

/**
 * Immutable snapshot of the configuration that an {@link Observarium} instance was built with.
 *
 * <p>Intended for introspection — for example, surfacing active settings in a health-check endpoint
 * or asserting configuration in integration tests. This record is populated by the {@link
 * Observarium.Builder} at build time and cannot be changed afterward.
 *
 * @param scrubLevel the active {@link ScrubLevel} controlling which categories of sensitive data
 *     are redacted from exception events before reporting; reflects the value passed to {@link
 *     Observarium.Builder#scrubLevel(ScrubLevel)}, or {@link ScrubLevel#BASIC} if none was set.
 *     Note that this field reflects the configured level even when a custom {@link
 *     io.hephaistos.observarium.scrub.DataScrubber} was supplied via {@link
 *     Observarium.Builder#scrubber}, in which case the actual scrubbing behavior is determined by
 *     that custom implementation.
 * @param postingServiceCount the number of {@link io.hephaistos.observarium.posting.PostingService}
 *     instances registered at build time; zero indicates that captured exceptions will not be
 *     forwarded anywhere
 * @param maxDuplicateComments the maximum number of duplicate comments Observarium will post on a
 *     single issue before stopping; {@link #UNLIMITED_COMMENTS} means no limit is applied
 */
public record ObservariumConfig(
    ScrubLevel scrubLevel, int postingServiceCount, int maxDuplicateComments) {

  /** Default value: 5 comments allowed per issue before the limit notice is posted. */
  public static final int DEFAULT_MAX_DUPLICATE_COMMENTS = 5;

  /** Sentinel value meaning unlimited comments (no cap). */
  public static final int UNLIMITED_COMMENTS = -1;

  /** Backward-compatible constructor that uses the default comment limit. */
  public ObservariumConfig(ScrubLevel scrubLevel, int postingServiceCount) {
    this(scrubLevel, postingServiceCount, DEFAULT_MAX_DUPLICATE_COMMENTS);
  }
}
