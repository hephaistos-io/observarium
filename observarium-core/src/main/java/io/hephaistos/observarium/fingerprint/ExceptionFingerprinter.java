package io.hephaistos.observarium.fingerprint;

/**
 * SPI for assigning a stable identity to a {@link Throwable} so that structurally equivalent
 * exceptions can be deduplicated across occurrences.
 *
 * <p>The fingerprint is the key used by {@link io.hephaistos.observarium.posting.PostingService} to
 * locate an existing issue before deciding whether to create a new one or comment on an existing
 * one. It must remain stable across restarts and deployments for deduplication to work correctly
 * over time.
 *
 * <p>Provide a custom implementation when the default hashing strategy produces too many or too few
 * collisions for your application's exception patterns.
 */
public interface ExceptionFingerprinter {

  /**
   * Computes a stable fingerprint for the given throwable.
   *
   * <p>The result must be <em>deterministic</em>: the same logical exception — same type, same call
   * sites, same cause chain — must always produce the same fingerprint, regardless of thread, time,
   * or JVM instance. Conversely, structurally different exceptions should produce different
   * fingerprints to avoid false deduplication.
   *
   * @param throwable the exception to fingerprint; never null
   * @return a non-null, non-empty string used as the deduplication key
   */
  String fingerprint(Throwable throwable);
}
