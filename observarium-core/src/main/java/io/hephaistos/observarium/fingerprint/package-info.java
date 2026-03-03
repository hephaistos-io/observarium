/**
 * Exception fingerprinting for deduplication across external issue trackers.
 *
 * <p>{@link io.hephaistos.observarium.fingerprint.ExceptionFingerprinter} defines the SPI
 * for computing a stable hash from an exception's identity. Implement this interface to
 * control how exceptions are grouped — for example, to ignore ephemeral message content
 * and key only on exception type and stack frames.
 * {@link io.hephaistos.observarium.fingerprint.DefaultExceptionFingerprinter} covers most
 * use-cases out of the box.
 */
package io.hephaistos.observarium.fingerprint;
