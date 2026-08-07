package io.hephaistos.observarium;

import static org.junit.jupiter.api.Assertions.*;

import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.event.Severity;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.scrub.ScrubLevel;
import io.hephaistos.observarium.trace.TraceContextProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ObservariumTest {

  // -----------------------------------------------------------------------
  // Stubs
  // -----------------------------------------------------------------------

  /** PostingService that always creates a new issue and records what it received. */
  private static class CapturingPostingService implements PostingService {
    final List<ExceptionEvent> received = new ArrayList<>();

    @Override
    public String name() {
      return "capturing";
    }

    @Override
    public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
      return DuplicateSearchResult.notFound();
    }

    @Override
    public PostingResult createIssue(ExceptionEvent event) {
      received.add(event);
      return PostingResult.success("ISSUE-1", "https://tracker/ISSUE-1");
    }

    @Override
    public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
      received.add(event);
      return PostingResult.success(externalIssueId, "https://tracker/" + externalIssueId);
    }
  }

  /** TraceContextProvider that returns fixed values. */
  private static class FixedTraceProvider implements TraceContextProvider {
    private final String traceId;
    private final String spanId;

    FixedTraceProvider(String traceId, String spanId) {
      this.traceId = traceId;
      this.spanId = spanId;
    }

    @Override
    public String getTraceId() {
      return traceId;
    }

    @Override
    public String getSpanId() {
      return spanId;
    }
  }

  /** PostingService that always throws from findDuplicate. */
  private static class ThrowingPostingService implements PostingService {
    @Override
    public String name() {
      return "thrower";
    }

    @Override
    public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
      throw new RuntimeException("service unavailable");
    }

    @Override
    public PostingResult createIssue(ExceptionEvent event) {
      return PostingResult.success("X-1", "https://tracker/X-1");
    }

    @Override
    public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
      return PostingResult.success(externalIssueId, "https://tracker/" + externalIssueId);
    }
  }

  // -----------------------------------------------------------------------
  // Builder defaults
  // -----------------------------------------------------------------------

  @Test
  void builder_build_withNoOptions_producesObservariumWithBasicScrubLevel() {
    Observarium obs = Observarium.builder().build();
    try {
      assertEquals(
          ScrubLevel.BASIC, obs.config().scrubLevel(), "Default scrub level must be BASIC");
    } finally {
      obs.shutdown();
    }
  }

  @Test
  void builder_build_withNoPostingServices_hasZeroServiceCount() {
    Observarium obs = Observarium.builder().build();
    try {
      assertEquals(0, obs.config().postingServiceCount());
    } finally {
      obs.shutdown();
    }
  }

  @Test
  void builder_scrubLevel_overridesDefault() {
    Observarium obs = Observarium.builder().scrubLevel(ScrubLevel.STRICT).build();
    try {
      assertEquals(ScrubLevel.STRICT, obs.config().scrubLevel());
    } finally {
      obs.shutdown();
    }
  }

  @Test
  void builder_addPostingService_countsInConfig() {
    CapturingPostingService svc = new CapturingPostingService();
    Observarium obs = Observarium.builder().addPostingService(svc).build();
    try {
      assertEquals(1, obs.config().postingServiceCount());
    } finally {
      obs.shutdown();
    }
  }

  @Test
  void builder_postingServices_replacesAll() {
    CapturingPostingService svc1 = new CapturingPostingService();
    CapturingPostingService svc2 = new CapturingPostingService();
    Observarium obs =
        Observarium.builder()
            .addPostingService(svc1) // added first
            .postingServices(List.of(svc2)) // replaces svc1
            .build();
    try {
      assertEquals(
          1, obs.config().postingServiceCount(), "postingServices() must replace, not append");
    } finally {
      obs.shutdown();
    }
  }

  @Test
  void builder_customFingerprinter_isUsed() throws Exception {
    CapturingPostingService svc = new CapturingPostingService();
    Observarium obs =
        Observarium.builder()
            .fingerprinter(t -> "custom-fingerprint")
            .addPostingService(svc)
            .build();
    try {
      obs.captureException(new RuntimeException("test")).get();
      assertFalse(svc.received.isEmpty());
      assertEquals("custom-fingerprint", svc.received.get(0).fingerprint());
    } finally {
      obs.shutdown();
    }
  }

  @Test
  void builder_customScrubber_isUsed() throws Exception {
    CapturingPostingService svc = new CapturingPostingService();
    Observarium obs =
        Observarium.builder()
            .scrubber(text -> text == null ? null : "SCRUBBED")
            .addPostingService(svc)
            .build();
    try {
      obs.captureException(new RuntimeException("sensitive")).get();
      assertFalse(svc.received.isEmpty());
      assertEquals("SCRUBBED", svc.received.get(0).message());
    } finally {
      obs.shutdown();
    }
  }

  @Test
  void builder_customTraceContextProvider_isUsed() throws Exception {
    CapturingPostingService svc = new CapturingPostingService();
    Observarium obs =
        Observarium.builder()
            .traceContextProvider(new FixedTraceProvider("t-123", "s-456"))
            .addPostingService(svc)
            .build();
    try {
      obs.captureException(new RuntimeException("traced")).get();
      assertFalse(svc.received.isEmpty());
      assertEquals("t-123", svc.received.get(0).traceId());
      assertEquals("s-456", svc.received.get(0).spanId());
    } finally {
      obs.shutdown();
    }
  }

  // -----------------------------------------------------------------------
  // captureException overloads
  // -----------------------------------------------------------------------

  @Test
  void captureException_throwableOnly_defaultsToErrorSeverity() throws Exception {
    CapturingPostingService svc = new CapturingPostingService();
    Observarium obs = Observarium.builder().addPostingService(svc).build();
    try {
      List<PostingResult> results = obs.captureException(new RuntimeException("e")).get();
      assertEquals(1, results.size());
      assertTrue(results.get(0).success());
      assertFalse(svc.received.isEmpty());
      assertEquals(Severity.ERROR, svc.received.get(0).severity());
    } finally {
      obs.shutdown();
    }
  }

  @Test
  void captureException_withSeverity_usesThatSeverity() throws Exception {
    CapturingPostingService svc = new CapturingPostingService();
    Observarium obs = Observarium.builder().addPostingService(svc).build();
    try {
      obs.captureException(new RuntimeException("e"), Severity.WARNING).get();
      assertEquals(Severity.WARNING, svc.received.get(0).severity());
    } finally {
      obs.shutdown();
    }
  }

  @Test
  void captureException_withSeverityAndTags_preservesBoth() throws Exception {
    CapturingPostingService svc = new CapturingPostingService();
    Observarium obs = Observarium.builder().addPostingService(svc).build();
    try {
      Map<String, String> tags = Map.of("env", "prod");
      obs.captureException(new RuntimeException("e"), Severity.FATAL, tags).get();
      ExceptionEvent event = svc.received.get(0);
      assertEquals(Severity.FATAL, event.severity());
      assertEquals("prod", event.tags().get("env"));
    } finally {
      obs.shutdown();
    }
  }

  @Test
  void captureException_returnsListWithOneResultPerService() throws Exception {
    CapturingPostingService svc1 = new CapturingPostingService();
    CapturingPostingService svc2 = new CapturingPostingService();
    Observarium obs = Observarium.builder().addPostingService(svc1).addPostingService(svc2).build();
    try {
      List<PostingResult> results = obs.captureException(new RuntimeException("e")).get();
      assertEquals(2, results.size());
    } finally {
      obs.shutdown();
    }
  }

  @Test
  void captureException_noServices_returnsEmptyList() throws Exception {
    Observarium obs = Observarium.builder().build();
    try {
      List<PostingResult> results = obs.captureException(new RuntimeException("e")).get();
      assertNotNull(results);
      assertTrue(results.isEmpty());
    } finally {
      obs.shutdown();
    }
  }

  @Test
  void captureException_serviceThrows_returnsFailureResult() throws Exception {
    Observarium obs = Observarium.builder().addPostingService(new ThrowingPostingService()).build();
    try {
      List<PostingResult> results = obs.captureException(new RuntimeException("e")).get();
      assertEquals(1, results.size());
      assertFalse(results.get(0).success());
      assertNotNull(results.get(0).errorMessage());
    } finally {
      obs.shutdown();
    }
  }

  // -----------------------------------------------------------------------
  // config() accessor
  // -----------------------------------------------------------------------

  @Test
  void config_returnsNonNull() {
    Observarium obs = Observarium.builder().build();
    try {
      assertNotNull(obs.config());
    } finally {
      obs.shutdown();
    }
  }

  // -----------------------------------------------------------------------
  // shutdown()
  // -----------------------------------------------------------------------

  @Test
  void shutdown_canBeCalledWithoutError() {
    Observarium obs = Observarium.builder().build();
    assertDoesNotThrow(obs::shutdown);
  }

  @Test
  void shutdown_isIdempotent() throws Exception {
    Observarium obs = Observarium.builder().build();
    // First call to shutdown.
    obs.shutdown();
    // Second call must not throw — calling shutdown on an already-terminated
    // ExecutorService is defined to be a no-op.
    assertDoesNotThrow(obs::shutdown);
  }

  @Test
  void shutdown_removesShutdownHook_noLeakOnRepeatedBuildAndShutdown() {
    // Building many instances and shutting them down should not accumulate
    // JVM shutdown hooks. This is a smoke test — if hooks leaked, the JVM
    // would eventually run out of memory or slow down during shutdown.
    for (int i = 0; i < 100; i++) {
      Observarium obs = Observarium.builder().build();
      obs.shutdown();
    }
    // If we reach here without error, no hook leak caused an ISE or OOM.
  }

  // -----------------------------------------------------------------------
  // queueCapacity builder option (smoke test — just verifies it doesn't throw)
  // -----------------------------------------------------------------------

  @Test
  void builder_queueCapacity_isApplied() throws Exception {
    CapturingPostingService svc = new CapturingPostingService();
    Observarium obs = Observarium.builder().queueCapacity(8).addPostingService(svc).build();
    try {
      List<PostingResult> results = obs.captureException(new RuntimeException("e")).get();
      assertEquals(1, results.size());
      assertTrue(results.get(0).success());
    } finally {
      obs.shutdown();
    }
  }

  // -----------------------------------------------------------------------
  // maxDuplicateComments builder option
  // -----------------------------------------------------------------------

  @Test
  void builder_maxDuplicateComments_storesInConfig() {
    Observarium obs = Observarium.builder().maxDuplicateComments(10).build();
    try {
      assertEquals(10, obs.config().maxDuplicateComments());
    } finally {
      obs.shutdown();
    }
  }

  @Test
  void builder_defaultMaxDuplicateComments_isFive() {
    Observarium obs = Observarium.builder().build();
    try {
      assertEquals(5, obs.config().maxDuplicateComments());
    } finally {
      obs.shutdown();
    }
  }

  @Test
  void builder_maxDuplicateComments_rejectsZero() {
    Observarium.Builder builder = Observarium.builder();
    assertThrows(IllegalArgumentException.class, () -> builder.maxDuplicateComments(0));
  }

  @Test
  void builder_maxDuplicateComments_rejectsNegativeTwo() {
    Observarium.Builder builder = Observarium.builder();
    assertThrows(IllegalArgumentException.class, () -> builder.maxDuplicateComments(-2));
  }

  @Test
  void builder_maxDuplicateComments_acceptsMinusOne() {
    // -1 is the sentinel value meaning unlimited comments.
    Observarium.Builder builder = Observarium.builder();
    assertDoesNotThrow(() -> builder.maxDuplicateComments(-1));
  }

  // -----------------------------------------------------------------------
  // addScrubPattern builder option
  // -----------------------------------------------------------------------

  @Test
  void builder_addScrubPattern_isForwardedToScrubber() throws Exception {
    // Pattern that redacts 4-digit numbers.
    Pattern digits = Pattern.compile("\\d{4}");
    CapturingPostingService svc = new CapturingPostingService();
    Observarium obs = Observarium.builder().addScrubPattern(digits).addPostingService(svc).build();
    try {
      obs.captureException(new RuntimeException("code 1234")).get();
      assertFalse(svc.received.isEmpty());
      // The 4-digit number in the message must have been replaced.
      assertFalse(
          svc.received.get(0).message().contains("1234"),
          "Custom scrub pattern must redact matching content in the exception message");
    } finally {
      obs.shutdown();
    }
  }

  // -----------------------------------------------------------------------
  // Resilience: fingerprinter/scrubber throwing (Gap 1)
  //
  // fingerprint() and scrub() are called inside buildEvent(), which runs
  // before the per-service loop. If either throws, the exception propagates
  // out of ExceptionProcessor.process(), which is the Supplier passed to
  // CompletableFuture.supplyAsync(). That causes the future to complete
  // exceptionally, and Observarium.captureException()'s .exceptionally()
  // handler catches it and maps it to a single failure PostingResult.
  // -----------------------------------------------------------------------

  @Test
  void captureException_fingerprintThrows_futureResolvesToFailureResult() throws Exception {
    // The fingerprinter is called inside buildEvent() before the posting-service loop.
    // When it throws, the future must NOT propagate the exception — the .exceptionally()
    // handler must catch it and return a failure PostingResult instead.
    Observarium obs =
        Observarium.builder()
            .fingerprinter(
                t -> {
                  throw new RuntimeException("fingerprint exploded");
                })
            .addPostingService(new CapturingPostingService())
            .build();
    try {
      List<PostingResult> results = obs.captureException(new RuntimeException("boom")).get();

      assertEquals(
          1,
          results.size(),
          "A single failure result must be returned when the fingerprinter throws");
      assertFalse(results.get(0).success(), "Result must be a failure when fingerprinter throws");
      assertNotNull(results.get(0).errorMessage(), "Failure result must carry an error message");
    } finally {
      obs.shutdown();
    }
  }

  @Test
  void captureException_fingerprintThrows_doesNotPropagateException() {
    // The CompletableFuture returned by captureException() must complete normally
    // (i.e. .get() must not throw ExecutionException) even when the fingerprinter throws.
    Observarium obs =
        Observarium.builder()
            .fingerprinter(
                t -> {
                  throw new RuntimeException("fingerprint exploded");
                })
            .build();
    try {
      assertDoesNotThrow(
          () -> obs.captureException(new RuntimeException("boom")).get(),
          "captureException future must not throw when the fingerprinter throws");
    } finally {
      obs.shutdown();
    }
  }

  @Test
  void captureException_scrubberThrows_futureResolvesToFailureResult() throws Exception {
    // scrub() is also called inside buildEvent(). Same contract: the .exceptionally()
    // handler must catch any throw and map it to a failure PostingResult.
    Observarium obs =
        Observarium.builder()
            .scrubber(
                text -> {
                  throw new RuntimeException("scrubber exploded");
                })
            .addPostingService(new CapturingPostingService())
            .build();
    try {
      List<PostingResult> results = obs.captureException(new RuntimeException("boom")).get();

      assertEquals(
          1, results.size(), "A single failure result must be returned when the scrubber throws");
      assertFalse(results.get(0).success(), "Result must be a failure when scrubber throws");
      assertNotNull(results.get(0).errorMessage(), "Failure result must carry an error message");
    } finally {
      obs.shutdown();
    }
  }

  @Test
  void captureException_scrubberThrows_doesNotPropagateException() {
    // Same as the fingerprinter variant: the future must complete normally.
    Observarium obs =
        Observarium.builder()
            .scrubber(
                text -> {
                  throw new RuntimeException("scrubber exploded");
                })
            .build();
    try {
      assertDoesNotThrow(
          () -> obs.captureException(new RuntimeException("boom")).get(),
          "captureException future must not throw when the scrubber throws");
    } finally {
      obs.shutdown();
    }
  }

  // -----------------------------------------------------------------------
  // ignoreIf builder option
  // -----------------------------------------------------------------------

  @Test
  void ignoreIf_matchingThrowable_isNotDelivered() throws Exception {
    CapturingPostingService svc = new CapturingPostingService();
    Observarium obs =
        Observarium.builder()
            .ignoreIf(t -> t instanceof IllegalStateException)
            .addPostingService(svc)
            .build();
    try {
      List<PostingResult> results =
          obs.captureException(new IllegalStateException("ignored")).get();
      assertTrue(results.isEmpty(), "Ignored throwable must resolve to an empty result list");
      assertTrue(svc.received.isEmpty(), "Ignored throwable must never reach a posting service");
    } finally {
      obs.shutdown();
    }
  }

  @Test
  void ignoreIf_nonMatchingThrowable_isStillDelivered() throws Exception {
    CapturingPostingService svc = new CapturingPostingService();
    Observarium obs =
        Observarium.builder()
            .ignoreIf(t -> t instanceof IllegalStateException)
            .addPostingService(svc)
            .build();
    try {
      List<PostingResult> results = obs.captureException(new RuntimeException("reported")).get();
      assertEquals(1, results.size());
      assertTrue(results.get(0).success());
      assertFalse(svc.received.isEmpty());
    } finally {
      obs.shutdown();
    }
  }

  @Test
  void ignoreIf_neverCalled_defaultsToIgnoringNothing() throws Exception {
    CapturingPostingService svc = new CapturingPostingService();
    Observarium obs = Observarium.builder().addPostingService(svc).build();
    try {
      List<PostingResult> results = obs.captureException(new RuntimeException("e")).get();
      assertEquals(1, results.size());
      assertFalse(svc.received.isEmpty());
    } finally {
      obs.shutdown();
    }
  }

  @Test
  void ignoreIf_multiplePredicates_areCombinedWithOr() throws Exception {
    // Neither predicate alone matches an ArithmeticException, but each matches a distinct type;
    // OR semantics mean registering both still lets each independently suppress its own type.
    CapturingPostingService svc = new CapturingPostingService();
    Observarium obs =
        Observarium.builder()
            .ignoreIf(t -> t instanceof IllegalStateException)
            .ignoreIf(t -> t instanceof IllegalArgumentException)
            .addPostingService(svc)
            .build();
    try {
      assertTrue(obs.captureException(new IllegalStateException()).get().isEmpty());
      assertTrue(obs.captureException(new IllegalArgumentException()).get().isEmpty());
      List<PostingResult> reported =
          obs.captureException(new RuntimeException("still reported")).get();
      assertEquals(1, reported.size());
      assertTrue(reported.get(0).success());
    } finally {
      obs.shutdown();
    }
  }

  @Test
  void ignoreIf_predicateThrows_failsOpenAndStillReports() throws Exception {
    CapturingPostingService svc = new CapturingPostingService();
    Observarium obs =
        Observarium.builder()
            .ignoreIf(
                t -> {
                  throw new RuntimeException("predicate exploded");
                })
            .addPostingService(svc)
            .build();
    try {
      List<PostingResult> results = obs.captureException(new RuntimeException("e")).get();
      assertEquals(
          1, results.size(), "A throwing predicate must fail open, not suppress reporting");
      assertTrue(results.get(0).success());
    } finally {
      obs.shutdown();
    }
  }

  @Test
  void ignoreIf_shortCircuitsBeforeFingerprintingAndScrubbing() throws Exception {
    // Fingerprinter and scrubber must never be invoked for an ignored throwable — proves the
    // short-circuit happens before either pipeline step, not merely before posting.
    java.util.concurrent.atomic.AtomicBoolean fingerprinterCalled =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    java.util.concurrent.atomic.AtomicBoolean scrubberCalled =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    Observarium obs =
        Observarium.builder()
            .ignoreIf(t -> true)
            .fingerprinter(
                t -> {
                  fingerprinterCalled.set(true);
                  return "fp";
                })
            .scrubber(
                text -> {
                  scrubberCalled.set(true);
                  return text;
                })
            .addPostingService(new CapturingPostingService())
            .build();
    try {
      obs.captureException(new RuntimeException("e")).get();
      assertFalse(fingerprinterCalled.get(), "Fingerprinter must not run for an ignored throwable");
      assertFalse(scrubberCalled.get(), "Scrubber must not run for an ignored throwable");
    } finally {
      obs.shutdown();
    }
  }

  // -----------------------------------------------------------------------
  // shutdownTimeout builder option
  // -----------------------------------------------------------------------

  @Test
  void builder_shutdownTimeout_rejectsZero() {
    Observarium.Builder builder = Observarium.builder();
    assertThrows(
        IllegalArgumentException.class, () -> builder.shutdownTimeout(java.time.Duration.ZERO));
  }

  @Test
  void builder_shutdownTimeout_rejectsNegative() {
    Observarium.Builder builder = Observarium.builder();
    assertThrows(
        IllegalArgumentException.class,
        () -> builder.shutdownTimeout(java.time.Duration.ofSeconds(-1)));
  }

  @Test
  void builder_shutdownTimeout_rejectsNull() {
    Observarium.Builder builder = Observarium.builder();
    assertThrows(IllegalArgumentException.class, () -> builder.shutdownTimeout(null));
  }

  @Test
  void shutdown_boundsDrainByConfiguredShutdownTimeout_withUnresponsivePostingService()
      throws Exception {
    // A posting service whose findDuplicate() blocks far longer than the configured timeout.
    // shutdown() must return close to the configured budget, not wait for the service.
    java.util.concurrent.CountDownLatch serviceEntered = new java.util.concurrent.CountDownLatch(1);
    PostingService unresponsive =
        new PostingService() {
          @Override
          public String name() {
            return "unresponsive";
          }

          @Override
          public DuplicateSearchResult findDuplicate(
              io.hephaistos.observarium.event.ExceptionEvent event) {
            serviceEntered.countDown();
            try {
              Thread.sleep(60_000);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            return DuplicateSearchResult.notFound();
          }

          @Override
          public PostingResult createIssue(io.hephaistos.observarium.event.ExceptionEvent event) {
            return PostingResult.success("X", "https://tracker/X");
          }

          @Override
          public PostingResult commentOnIssue(
              String externalIssueId, io.hephaistos.observarium.event.ExceptionEvent event) {
            return PostingResult.success(externalIssueId, "https://tracker/" + externalIssueId);
          }
        };

    Observarium obs =
        Observarium.builder()
            .shutdownTimeout(java.time.Duration.ofMillis(300))
            .addPostingService(unresponsive)
            .build();

    obs.captureException(new RuntimeException("stuck"));
    assertTrue(serviceEntered.await(5, java.util.concurrent.TimeUnit.SECONDS));

    long start = System.nanoTime();
    obs.shutdown();
    long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

    assertTrue(
        elapsedMillis < 5_000,
        "shutdown() must be bounded by the configured shutdownTimeout, took "
            + elapsedMillis
            + "ms");
  }
}
