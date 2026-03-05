package io.hephaistos.observarium.event;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExceptionEventTest {

  // -----------------------------------------------------------------------
  // Builder — explicit values are stored correctly
  // -----------------------------------------------------------------------

  @Test
  void builder_storesAllExplicitFields() {
    Instant now = Instant.parse("2024-06-01T12:00:00Z");

    ExceptionEvent event =
        ExceptionEvent.builder()
            .fingerprint("fp-123")
            .exceptionClass("com.example.MyException")
            .message("something failed")
            .stackTrace(List.of("com.example.App.main(App.java:10)"))
            .rawStackTrace("com.example.MyException: something failed\n\tat com.example.App.main")
            .severity(Severity.FATAL)
            .timestamp(now)
            .threadName("worker-1")
            .traceId("trace-001")
            .spanId("span-002")
            .tags(Map.of("env", "production"))
            .extra(Map.of("java.version", "21"))
            .build();

    assertAll(
        "all explicitly set fields must be preserved",
        () -> assertEquals("fp-123", event.fingerprint()),
        () -> assertEquals("com.example.MyException", event.exceptionClass()),
        () -> assertEquals("something failed", event.message()),
        () -> assertEquals(1, event.stackTrace().size()),
        () -> assertEquals("com.example.App.main(App.java:10)", event.stackTrace().get(0)),
        () -> assertNotNull(event.rawStackTrace()),
        () -> assertEquals(Severity.FATAL, event.severity()),
        () -> assertEquals(now, event.timestamp()),
        () -> assertEquals("worker-1", event.threadName()),
        () -> assertEquals("trace-001", event.traceId()),
        () -> assertEquals("span-002", event.spanId()),
        () -> assertEquals("production", event.tags().get("env")),
        () -> assertEquals("21", event.extra().get("java.version")));
  }

  // -----------------------------------------------------------------------
  // Builder defaults
  // -----------------------------------------------------------------------

  @Test
  void builder_defaultSeverityIsError() {
    ExceptionEvent event = ExceptionEvent.builder().build();
    assertEquals(Severity.ERROR, event.severity(), "Default severity must be ERROR");
  }

  @Test
  void builder_defaultStackTraceIsEmptyList() {
    ExceptionEvent event = ExceptionEvent.builder().build();
    assertNotNull(event.stackTrace(), "stackTrace must not be null by default");
    assertTrue(event.stackTrace().isEmpty(), "Default stackTrace must be empty");
  }

  @Test
  void builder_defaultTagsIsEmptyMap() {
    ExceptionEvent event = ExceptionEvent.builder().build();
    assertNotNull(event.tags(), "tags must not be null by default");
    assertTrue(event.tags().isEmpty(), "Default tags must be empty");
  }

  @Test
  void builder_defaultExtraIsEmptyMap() {
    ExceptionEvent event = ExceptionEvent.builder().build();
    assertNotNull(event.extra(), "extra must not be null by default");
    assertTrue(event.extra().isEmpty(), "Default extra must be empty");
  }

  @Test
  void builder_defaultTimestampIsPopulated() {
    Instant before = Instant.now();
    ExceptionEvent event = ExceptionEvent.builder().build();
    Instant after = Instant.now();

    assertNotNull(event.timestamp(), "Default timestamp must not be null");
    assertFalse(
        event.timestamp().isBefore(before),
        "Default timestamp must not be before the test started");
    assertFalse(
        event.timestamp().isAfter(after), "Default timestamp must not be after the test completed");
  }

  // -----------------------------------------------------------------------
  // Immutability guarantees
  // -----------------------------------------------------------------------

  @Test
  void stackTrace_isCopiedOnSet_originalMutationDoesNotAffectEvent() {
    List<String> mutable = new java.util.ArrayList<>(List.of("frame-1"));
    ExceptionEvent event = ExceptionEvent.builder().stackTrace(mutable).build();

    mutable.add("frame-2"); // mutate the original list

    assertEquals(
        1,
        event.stackTrace().size(),
        "Mutating the original list must not affect the stored stackTrace");
  }

  @Test
  void tags_isCopiedOnSet_originalMutationDoesNotAffectEvent() {
    Map<String, String> mutable = new java.util.HashMap<>(Map.of("k", "v"));
    ExceptionEvent event = ExceptionEvent.builder().tags(mutable).build();

    mutable.put("k2", "v2");

    assertEquals(
        1, event.tags().size(), "Mutating the original map must not affect the stored tags");
  }

  @Test
  void extra_isCopiedOnSet_originalMutationDoesNotAffectEvent() {
    Map<String, String> mutable = new java.util.HashMap<>(Map.of("x", "y"));
    ExceptionEvent event = ExceptionEvent.builder().extra(mutable).build();

    mutable.put("x2", "y2");

    assertEquals(
        1, event.extra().size(), "Mutating the original map must not affect the stored extra");
  }

  // -----------------------------------------------------------------------
  // Optional / nullable fields
  // -----------------------------------------------------------------------

  @Test
  void message_canBeNull() {
    ExceptionEvent event = ExceptionEvent.builder().message(null).build();
    assertNull(event.message());
  }

  @Test
  void traceId_canBeNull() {
    ExceptionEvent event = ExceptionEvent.builder().traceId(null).build();
    assertNull(event.traceId());
  }

  @Test
  void spanId_canBeNull() {
    ExceptionEvent event = ExceptionEvent.builder().spanId(null).build();
    assertNull(event.spanId());
  }

  // -----------------------------------------------------------------------
  // Record equality
  // -----------------------------------------------------------------------

  @Test
  void twoEventsWithSameFields_areEqual() {
    Instant ts = Instant.parse("2024-01-01T00:00:00Z");

    ExceptionEvent a =
        ExceptionEvent.builder()
            .fingerprint("fp")
            .exceptionClass("Ex")
            .message("msg")
            .severity(Severity.INFO)
            .timestamp(ts)
            .threadName("t")
            .build();

    ExceptionEvent b =
        ExceptionEvent.builder()
            .fingerprint("fp")
            .exceptionClass("Ex")
            .message("msg")
            .severity(Severity.INFO)
            .timestamp(ts)
            .threadName("t")
            .build();

    assertEquals(a, b, "Two ExceptionEvent records with identical fields must be equal");
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void twoEventsWithDifferentFingerprints_areNotEqual() {
    Instant ts = Instant.parse("2024-01-01T00:00:00Z");

    ExceptionEvent a = ExceptionEvent.builder().fingerprint("fp-1").timestamp(ts).build();
    ExceptionEvent b = ExceptionEvent.builder().fingerprint("fp-2").timestamp(ts).build();

    assertNotEquals(a, b);
  }
}
