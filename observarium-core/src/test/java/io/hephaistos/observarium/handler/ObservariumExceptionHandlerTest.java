package io.hephaistos.observarium.handler;

import static org.junit.jupiter.api.Assertions.*;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.event.Severity;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ObservariumExceptionHandlerTest {

  // Track Observarium instances created so we can shut them down after each test.
  private final List<Observarium> created = new ArrayList<>();

  @AfterEach
  void shutdownAll() {
    created.forEach(Observarium::shutdown);
    // Restore the default uncaught exception handler so one test's install()
    // call does not pollute subsequent tests.
    Thread.setDefaultUncaughtExceptionHandler(null);
  }

  // -----------------------------------------------------------------------
  // Stubs
  // -----------------------------------------------------------------------

  /** Records received events and the severity they were submitted with. */
  private static class CapturingPostingService implements PostingService {
    final List<ExceptionEvent> received = new ArrayList<>();
    final List<Severity> severities = new ArrayList<>();

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
      severities.add(event.severity());
      return PostingResult.success("ISSUE-1", "https://tracker/ISSUE-1");
    }

    @Override
    public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
      received.add(event);
      severities.add(event.severity());
      return PostingResult.success(externalIssueId, "https://tracker/" + externalIssueId);
    }
  }

  /** Records every uncaughtException invocation. */
  private static class RecordingUncaughtHandler implements Thread.UncaughtExceptionHandler {
    final List<Throwable> received = new ArrayList<>();
    final List<Thread> threads = new ArrayList<>();

    @Override
    public void uncaughtException(Thread t, Throwable e) {
      threads.add(t);
      received.add(e);
    }
  }

  /**
   * PostingService that blocks inside createIssue until a release latch is counted down. Used to
   * hold the worker thread so that the 5-second get() in the handler times out.
   */
  private static class BlockingPostingService implements PostingService {
    private final CountDownLatch releaseLatch;

    BlockingPostingService(CountDownLatch releaseLatch) {
      this.releaseLatch = releaseLatch;
    }

    @Override
    public String name() {
      return "blocking";
    }

    @Override
    public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
      return DuplicateSearchResult.notFound();
    }

    @Override
    public PostingResult createIssue(ExceptionEvent event) {
      try {
        releaseLatch.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return PostingResult.success("ISSUE-1", "https://tracker/ISSUE-1");
    }

    @Override
    public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
      return PostingResult.success(externalIssueId, "https://tracker/" + externalIssueId);
    }
  }

  private Observarium buildObservarium(PostingService... services) {
    Observarium.Builder builder = Observarium.builder();
    for (PostingService svc : services) {
      builder.addPostingService(svc);
    }
    Observarium obs = builder.build();
    created.add(obs);
    return obs;
  }

  // -----------------------------------------------------------------------
  // Constructor variants
  // -----------------------------------------------------------------------

  @Test
  void singleArgConstructor_doesNotThrow() {
    Observarium obs = buildObservarium();
    assertDoesNotThrow(() -> new ObservariumExceptionHandler(obs));
  }

  @Test
  void twoArgConstructor_withNullDelegate_doesNotThrow() {
    Observarium obs = buildObservarium();
    assertDoesNotThrow(() -> new ObservariumExceptionHandler(obs, null));
  }

  @Test
  void twoArgConstructor_withDelegate_doesNotThrow() {
    Observarium obs = buildObservarium();
    RecordingUncaughtHandler delegate = new RecordingUncaughtHandler();
    assertDoesNotThrow(() -> new ObservariumExceptionHandler(obs, delegate));
  }

  // -----------------------------------------------------------------------
  // uncaughtException() — captures with FATAL severity
  // -----------------------------------------------------------------------

  @Test
  void uncaughtException_capturesWithFatalSeverity() throws InterruptedException {
    CapturingPostingService svc = new CapturingPostingService();
    Observarium obs = buildObservarium(svc);
    ObservariumExceptionHandler handler = new ObservariumExceptionHandler(obs);

    RuntimeException exception = new RuntimeException("crash");
    handler.uncaughtException(Thread.currentThread(), exception);

    assertFalse(svc.severities.isEmpty(), "Handler must submit the exception for capture");
    assertEquals(
        Severity.FATAL,
        svc.severities.get(0),
        "Uncaught exceptions must be captured with FATAL severity");
  }

  @Test
  void uncaughtException_blocksUntilCaptureComplete() {
    // The handler calls .get() on the future, so the service must have been
    // called by the time uncaughtException() returns.
    CapturingPostingService svc = new CapturingPostingService();
    Observarium obs = buildObservarium(svc);
    ObservariumExceptionHandler handler = new ObservariumExceptionHandler(obs);

    handler.uncaughtException(Thread.currentThread(), new RuntimeException("blocking test"));

    assertFalse(
        svc.received.isEmpty(),
        "The posting service must have been called before uncaughtException() returns");
  }

  // -----------------------------------------------------------------------
  // uncaughtException() — delegate forwarding
  // -----------------------------------------------------------------------

  @Test
  void uncaughtException_withDelegate_forwardsToDelegate() {
    CapturingPostingService svc = new CapturingPostingService();
    Observarium obs = buildObservarium(svc);
    RecordingUncaughtHandler delegate = new RecordingUncaughtHandler();
    ObservariumExceptionHandler handler = new ObservariumExceptionHandler(obs, delegate);

    RuntimeException exception = new RuntimeException("delegated");
    handler.uncaughtException(Thread.currentThread(), exception);

    assertEquals(1, delegate.received.size(), "Delegate must be called once");
    assertSame(exception, delegate.received.get(0), "Delegate must receive the original exception");
    assertSame(
        Thread.currentThread(),
        delegate.threads.get(0),
        "Delegate must receive the original thread");
  }

  @Test
  void uncaughtException_withNullDelegate_doesNotThrow() {
    Observarium obs = buildObservarium();
    ObservariumExceptionHandler handler = new ObservariumExceptionHandler(obs, null);
    // Must not throw NullPointerException when delegate is null.
    assertDoesNotThrow(
        () ->
            handler.uncaughtException(Thread.currentThread(), new RuntimeException("no delegate")));
  }

  // -----------------------------------------------------------------------
  // install()
  // -----------------------------------------------------------------------

  @Test
  void install_setsDefaultUncaughtExceptionHandler() {
    Observarium obs = buildObservarium();
    ObservariumExceptionHandler.install(obs);

    Thread.UncaughtExceptionHandler installed = Thread.getDefaultUncaughtExceptionHandler();
    assertNotNull(installed, "install() must set a default uncaught exception handler");
    assertInstanceOf(
        ObservariumExceptionHandler.class,
        installed,
        "The installed handler must be an ObservariumExceptionHandler");
  }

  @Test
  void install_wrapsExistingDefaultHandler() {
    // Set up an existing handler first.
    RecordingUncaughtHandler existingHandler = new RecordingUncaughtHandler();
    Thread.setDefaultUncaughtExceptionHandler(existingHandler);

    Observarium obs = buildObservarium();
    ObservariumExceptionHandler.install(obs);

    // The new handler must be an ObservariumExceptionHandler (it wraps the existing one).
    Thread.UncaughtExceptionHandler installed = Thread.getDefaultUncaughtExceptionHandler();
    assertInstanceOf(ObservariumExceptionHandler.class, installed);

    // Trigger it and verify the original handler still runs.
    RuntimeException ex = new RuntimeException("chained");
    installed.uncaughtException(Thread.currentThread(), ex);

    assertEquals(
        1,
        existingHandler.received.size(),
        "The previously installed handler must still be invoked after install()");
    assertSame(ex, existingHandler.received.get(0));
  }

  // -----------------------------------------------------------------------
  // Resilience: delivery failure (Gap 3)
  //
  // The handler calls captureException(...).get(5, SECONDS). If that get()
  // throws (TimeoutException, InterruptedException, ExecutionException), the
  // catch block must swallow it so that uncaughtException() never propagates
  // an exception to the JVM. The delegate must still be called afterwards.
  // -----------------------------------------------------------------------

  /**
   * When the calling thread has been interrupted before uncaughtException() runs, the .get() call
   * throws InterruptedException. The handler must catch it, must not re-throw, and must still
   * invoke the delegate.
   *
   * <p>We restore the thread's interrupted flag after the test so later tests are not affected.
   */
  @Test
  void uncaughtException_interruptedDuringDelivery_doesNotThrowAndCallsDelegate()
      throws InterruptedException {
    CapturingPostingService svc = new CapturingPostingService();
    Observarium obs = buildObservarium(svc);
    RecordingUncaughtHandler delegate = new RecordingUncaughtHandler();
    ObservariumExceptionHandler handler = new ObservariumExceptionHandler(obs, delegate);

    AtomicBoolean threw = new AtomicBoolean(false);
    CountDownLatch testDone = new CountDownLatch(1);

    // Run uncaughtException on a separate thread so we can interrupt it cleanly
    // without polluting the test thread's interrupt status.
    Thread runner =
        new Thread(
            () -> {
              // Pre-interrupt this thread so that .get() immediately throws InterruptedException.
              Thread.currentThread().interrupt();
              try {
                handler.uncaughtException(Thread.currentThread(), new RuntimeException("crash"));
              } catch (Exception unexpected) {
                threw.set(true);
              } finally {
                testDone.countDown();
              }
            },
            "handler-test-thread");

    runner.start();
    assertTrue(testDone.await(10, java.util.concurrent.TimeUnit.SECONDS),
        "Handler thread must complete within 10 s");

    assertFalse(threw.get(), "uncaughtException must not throw even when delivery is interrupted");
    assertEquals(1, delegate.received.size(), "Delegate must still be called after delivery failure");
  }

  /**
   * When the .get() call on the future times out (because the posting service is too slow),
   * uncaughtException() must catch the TimeoutException, must not re-throw, and must still invoke
   * the delegate.
   *
   * <p>A posting service that never releases its latch is used to guarantee the 5-second timeout
   * elapses. This test takes slightly over 5 seconds by design and is tagged accordingly.
   */
  @Test
  @org.junit.jupiter.api.Timeout(15)
  void uncaughtException_deliveryTimesOut_doesNotThrowAndCallsDelegate() throws InterruptedException {
    // The latch is never released, so createIssue blocks indefinitely and the
    // handler's 5-second .get() timeout will expire.
    CountDownLatch neverReleased = new CountDownLatch(1);
    BlockingPostingService svc = new BlockingPostingService(neverReleased);
    Observarium obs = buildObservarium(svc);
    RecordingUncaughtHandler delegate = new RecordingUncaughtHandler();
    ObservariumExceptionHandler handler = new ObservariumExceptionHandler(obs, delegate);

    AtomicBoolean threw = new AtomicBoolean(false);
    CountDownLatch testDone = new CountDownLatch(1);

    Thread runner =
        new Thread(
            () -> {
              try {
                handler.uncaughtException(Thread.currentThread(), new RuntimeException("slow crash"));
              } catch (Exception unexpected) {
                threw.set(true);
              } finally {
                // Release the latch so the worker thread can exit cleanly.
                neverReleased.countDown();
                testDone.countDown();
              }
            },
            "handler-timeout-test-thread");

    runner.start();
    // Allow up to 10 s for the handler to time out (FATAL_WAIT_SECONDS=5) and return.
    assertTrue(testDone.await(10, java.util.concurrent.TimeUnit.SECONDS),
        "Handler thread must return after the 5-second delivery timeout");

    assertFalse(threw.get(), "uncaughtException must not throw when delivery times out");
    assertEquals(1, delegate.received.size(), "Delegate must still be called after delivery timeout");
  }
}
