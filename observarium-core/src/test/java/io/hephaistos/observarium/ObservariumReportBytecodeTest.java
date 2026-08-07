package io.hephaistos.observarium;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Verifies the bytecode acceptance criterion of issue #15: "A class calling only {@code
 * report(...)} has no reference to {@code CompletableFuture} in its compiled bytecode."
 *
 * <p>{@code javac} resolves an invoked method's descriptor at compile time and writes it into the
 * constant pool of the calling class, independent of whether the return value is used. So the only
 * way to prove {@code report(...)} does not smuggle a {@code CompletableFuture} reference into
 * callers is to inspect compiled bytecode directly, rather than reflecting on {@code
 * Observarium.class} (which legitimately references {@code CompletableFuture} for {@code
 * captureException}).
 */
class ObservariumReportBytecodeTest {

  private static final String COMPLETABLE_FUTURE_BINARY_NAME =
      "java/util/concurrent/CompletableFuture";

  @Test
  void reportOnlyCaller_bytecodeHasNoCompletableFutureReference() throws IOException {
    byte[] classBytes = readClassBytes(ReportOnlyCaller.class);

    // A JVM class file's constant pool stores UTF-8 entries (e.g. class binary names, method
    // descriptors) as raw modified-UTF-8 byte sequences. If the compiler had emitted any
    // constant-pool reference to CompletableFuture — as it would for a call site whose method
    // descriptor mentions it — the class's binary name would appear verbatim in those bytes.
    String haystack = new String(classBytes, StandardCharsets.ISO_8859_1);

    assertFalse(
        haystack.contains(COMPLETABLE_FUTURE_BINARY_NAME),
        "ReportOnlyCaller.class must not reference CompletableFuture — report(...) must be void"
            + " at the call site");
  }

  @Test
  void observarium_bytecodeDoesReferenceCompletableFuture_sanityCheck() throws IOException {
    // Sanity check for the test method above: Observarium itself legitimately declares
    // captureException methods returning CompletableFuture, so its own class file *must*
    // contain the reference. This guards against the byte-scan technique silently matching
    // nothing due to a wrong resource path or similar mistake.
    byte[] classBytes = readClassBytes(Observarium.class);
    String haystack = new String(classBytes, StandardCharsets.ISO_8859_1);

    assertTrue(
        haystack.contains(COMPLETABLE_FUTURE_BINARY_NAME),
        "Sanity check failed: Observarium.class is expected to reference CompletableFuture"
            + " because captureException(...) returns it");
  }

  private static byte[] readClassBytes(Class<?> type) throws IOException {
    String resourceName = type.getSimpleName() + ".class";
    try (InputStream in = type.getResourceAsStream(resourceName)) {
      if (in == null) {
        throw new IOException("Could not locate compiled class resource: " + resourceName);
      }
      return in.readAllBytes();
    }
  }
}
