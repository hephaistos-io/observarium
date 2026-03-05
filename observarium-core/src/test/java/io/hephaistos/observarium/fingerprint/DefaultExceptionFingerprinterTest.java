package io.hephaistos.observarium.fingerprint;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultExceptionFingerprinterTest {

  private DefaultExceptionFingerprinter fingerprinter;

  @BeforeEach
  void setUp() {
    fingerprinter = new DefaultExceptionFingerprinter();
  }

  // --- helpers -----------------------------------------------------------

  /**
   * Manufactures a Throwable whose stack trace is fixed and deterministic, independent of the line
   * number where it is created. We do this by replacing the real stack trace with a hand-crafted
   * one, which also lets us produce two traces that differ only in line numbers.
   */
  private static RuntimeException exceptionWithFixedTrace(String message, int lineNumber) {
    RuntimeException ex = new RuntimeException(message);
    ex.setStackTrace(
        new StackTraceElement[] {
          new StackTraceElement("com.example.Service", "doWork", "Service.java", lineNumber),
          new StackTraceElement(
              "com.example.Controller", "handle", "Controller.java", lineNumber + 10)
        });
    return ex;
  }

  private static IllegalArgumentException differentTypeException() {
    IllegalArgumentException ex = new IllegalArgumentException("bad arg");
    ex.setStackTrace(
        new StackTraceElement[] {
          new StackTraceElement("com.example.Service", "doWork", "Service.java", 42)
        });
    return ex;
  }

  // --- tests -------------------------------------------------------------

  @Test
  void sameExceptionClassAndMethods_produceSameFingerprint() {
    RuntimeException ex1 = exceptionWithFixedTrace("msg A", 100);
    RuntimeException ex2 = exceptionWithFixedTrace("msg B", 100);

    String fp1 = fingerprinter.fingerprint(ex1);
    String fp2 = fingerprinter.fingerprint(ex2);

    assertEquals(
        fp1, fp2, "Same exception class and same stack frames must produce the same fingerprint");
  }

  @Test
  void differentExceptionTypes_produceDifferentFingerprints() {
    RuntimeException rte = exceptionWithFixedTrace("msg", 42);
    IllegalArgumentException iae = differentTypeException();

    String fp1 = fingerprinter.fingerprint(rte);
    String fp2 = fingerprinter.fingerprint(iae);

    assertNotEquals(fp1, fp2, "Different exception types must produce different fingerprints");
  }

  @Test
  void sameExceptionAtDifferentLineNumbers_produceSameFingerprint() {
    // Line numbers differ but class names and method names are identical.
    RuntimeException atLine10 = exceptionWithFixedTrace("same message", 10);
    RuntimeException atLine999 = exceptionWithFixedTrace("same message", 999);

    String fp1 = fingerprinter.fingerprint(atLine10);
    String fp2 = fingerprinter.fingerprint(atLine999);

    assertEquals(fp1, fp2, "Line number differences must not affect the fingerprint");
  }

  @Test
  void exceptionWithCause_fingerprintDiffersFromExceptionWithoutCause() {
    RuntimeException withoutCause = exceptionWithFixedTrace("top", 1);

    IllegalStateException cause = new IllegalStateException("root");
    cause.setStackTrace(
        new StackTraceElement[] {
          new StackTraceElement("com.example.Dao", "query", "Dao.java", 77)
        });
    RuntimeException withCause = exceptionWithFixedTrace("top", 1);
    withCause.initCause(cause);

    String fpNoCause = fingerprinter.fingerprint(withoutCause);
    String fpWithCause = fingerprinter.fingerprint(withCause);

    assertNotEquals(
        fpNoCause, fpWithCause, "Adding a cause to an exception must change the fingerprint");
  }

  @Test
  void causeChainIsIncluded_deeperCauseChangesFingerprint() {
    // Two exceptions with the same top-level frame but different cause chains.
    IllegalStateException causeA = new IllegalStateException("cause A");
    causeA.setStackTrace(new StackTraceElement[0]);

    NullPointerException causeB = new NullPointerException("cause B");
    causeB.setStackTrace(new StackTraceElement[0]);

    RuntimeException withCauseA = exceptionWithFixedTrace("top", 1);
    withCauseA.initCause(causeA);

    RuntimeException withCauseB = exceptionWithFixedTrace("top", 1);
    withCauseB.initCause(causeB);

    String fpA = fingerprinter.fingerprint(withCauseA);
    String fpB = fingerprinter.fingerprint(withCauseB);

    assertNotEquals(fpA, fpB, "Different cause types must produce different fingerprints");
  }

  @Test
  void fingerprint_isValidHexSha256String() {
    RuntimeException ex = exceptionWithFixedTrace("msg", 5);
    String fp = fingerprinter.fingerprint(ex);

    assertNotNull(fp);
    assertEquals(64, fp.length(), "SHA-256 hex string must be exactly 64 characters");
    assertTrue(
        fp.matches("[0-9a-f]{64}"), "Fingerprint must contain only lowercase hex characters");
  }

  @Test
  void fingerprint_isDeterministic_acrossMultipleCalls() {
    RuntimeException ex = exceptionWithFixedTrace("deterministic", 50);
    String fp1 = fingerprinter.fingerprint(ex);
    String fp2 = fingerprinter.fingerprint(ex);
    assertEquals(fp1, fp2, "Fingerprinting the same exception twice must yield the same result");
  }
}
