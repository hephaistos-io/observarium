package io.hephaistos.observarium;

import static org.junit.jupiter.api.Assertions.*;

import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ObservariumConcurrencyTest {

  // -----------------------------------------------------------------------
  // Stubs
  // -----------------------------------------------------------------------

  /**
   * PostingService that records every processed event in a thread-safe queue and optionally blocks
   * inside createIssue until a release latch is counted down, allowing tests to control timing.
   *
   * <p>An optional "entered" latch is counted down as soon as the worker enters createIssue (before
   * it blocks on the release latch), giving the test a reliable signal that the worker is in-flight
   * and blocked.
   */
  private static class LatchedPostingService implements PostingService {
    final ConcurrentLinkedQueue<ExceptionEvent> received = new ConcurrentLinkedQueue<>();
    final AtomicInteger createIssueCallCount = new AtomicInteger();

    /** Counted down when createIssue is entered, before blocking on releaseLatch. */
    private final CountDownLatch enteredLatch;

    /** When non-null, createIssue blocks here until the latch is released. */
    private final CountDownLatch releaseLatch;

    LatchedPostingService() {
      this.enteredLatch = null;
      this.releaseLatch = null;
    }

    LatchedPostingService(CountDownLatch enteredLatch, CountDownLatch releaseLatch) {
      this.enteredLatch = enteredLatch;
      this.releaseLatch = releaseLatch;
    }

    @Override
    public String name() {
      return "latched";
    }

    @Override
    public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
      return DuplicateSearchResult.notFound();
    }

    @Override
    public PostingResult createIssue(ExceptionEvent event) {
      if (enteredLatch != null) {
        enteredLatch.countDown();
      }
      if (releaseLatch != null) {
        try {
          releaseLatch.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      received.add(event);
      createIssueCallCount.incrementAndGet();
      return PostingResult.success("ISSUE-1", "https://tracker/ISSUE-1");
    }

    @Override
    public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
      received.add(event);
      createIssueCallCount.incrementAndGet();
      return PostingResult.success(externalIssueId, "https://tracker/" + externalIssueId);
    }
  }

  // -----------------------------------------------------------------------
  // Tests
  // -----------------------------------------------------------------------

  /**
   * N threads each submit one exception concurrently. All N must be processed by the posting
   * service.
   *
   * <p>A CountDownLatch is used as a starting gun so that all threads begin submitting at as close
   * to the same instant as possible, maximising contention on the executor queue.
   */
  @Test
  void captureException_fromMultipleThreads_allProcessed() throws Exception {
    final int threadCount = 20;
    LatchedPostingService svc = new LatchedPostingService();
    Observarium obs = Observarium.builder().addPostingService(svc).build();

    CountDownLatch startGun = new CountDownLatch(1);
    CountDownLatch allSubmitted = new CountDownLatch(threadCount);
    List<CompletableFuture<List<PostingResult>>> futures = new ArrayList<>(threadCount);

    try {
      // Pre-create all threads so they are ready and waiting before we fire the start gun.
      List<Thread> threads = new ArrayList<>(threadCount);
      for (int i = 0; i < threadCount; i++) {
        final int index = i;
        Thread t =
            new Thread(
                () -> {
                  try {
                    startGun.await();
                    CompletableFuture<List<PostingResult>> f =
                        obs.captureException(new RuntimeException("concurrent-" + index));
                    synchronized (futures) {
                      futures.add(f);
                    }
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  } finally {
                    allSubmitted.countDown();
                  }
                },
                "test-thread-" + i);
        threads.add(t);
      }

      threads.forEach(Thread::start);

      // Fire the starting gun — all threads unblock simultaneously.
      startGun.countDown();

      // Wait for all threads to finish submitting (with a generous timeout).
      assertTrue(
          allSubmitted.await(5, java.util.concurrent.TimeUnit.SECONDS),
          "All threads must submit within 5 s");

      // Now wait for all futures to complete so the executor has drained.
      List<CompletableFuture<List<PostingResult>>> snapshot;
      synchronized (futures) {
        snapshot = new ArrayList<>(futures);
      }
      CompletableFuture.allOf(snapshot.toArray(new CompletableFuture[0]))
          .get(5, java.util.concurrent.TimeUnit.SECONDS);

      assertEquals(
          threadCount,
          svc.createIssueCallCount.get(),
          "Every concurrently submitted exception must be processed exactly once");
    } finally {
      obs.shutdown();
    }
  }

  /**
   * When the queue is full, new submissions are dropped and the returned future completes
   * immediately with a failure result. The caller must not receive an exception on its thread.
   *
   * <p>Strategy:
   *
   * <ol>
   *   <li>Create an Observarium with queueCapacity(1) and a blocking posting service.
   *   <li>Submit one exception to occupy the single queue slot and block the worker thread.
   *   <li>Submit several more exceptions while the queue is full. These are dropped.
   *   <li>Verify none of those captureException() calls threw.
   *   <li>Verify dropped futures complete with a failure result (not hang forever).
   *   <li>Unblock the worker, verify the first future resolves successfully.
   * </ol>
   */
  @Test
  void captureException_queueFull_doesNotThrowOnCallerThread() throws Exception {
    CountDownLatch workerEntered = new CountDownLatch(1);
    CountDownLatch releaseWorker = new CountDownLatch(1);
    LatchedPostingService svc = new LatchedPostingService(workerEntered, releaseWorker);

    // A capacity of 1 means: 1 slot in the queue. The worker picks up the first task
    // immediately (so the queue empties), then blocks. A second submission fills the queue,
    // and a third submission arrives when both the worker and the queue slot are occupied.
    Observarium obs = Observarium.builder().queueCapacity(1).addPostingService(svc).build();

    try {
      // Submit the first exception — the worker picks it up and blocks on workerEntered.
      CompletableFuture<List<PostingResult>> firstFuture =
          obs.captureException(new RuntimeException("first"));

      // Wait until the worker is genuinely inside createIssue and blocked.
      assertTrue(
          workerEntered.await(5, java.util.concurrent.TimeUnit.SECONDS),
          "Worker must enter createIssue within 5 s");

      // Submit more exceptions while the worker is blocked. Some or all will be dropped.
      // The critical assertion is that none of these calls throws.
      List<RuntimeException> thrown = new ArrayList<>();
      List<CompletableFuture<List<PostingResult>>> droppedFutures = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        final int index = i;
        try {
          droppedFutures.add(obs.captureException(new RuntimeException("overflow-" + index)));
        } catch (RuntimeException e) {
          thrown.add(e);
        }
      }

      assertTrue(
          thrown.isEmpty(),
          "captureException must never throw on the caller's thread, even when the queue is full; "
              + "got: "
              + thrown);

      // Some futures may have been queued (not rejected), so they won't be done yet.
      // The truly rejected ones must be completed immediately with a failure result.
      long rejectedCount = droppedFutures.stream().filter(CompletableFuture::isDone).count();
      assertTrue(
          rejectedCount > 0,
          "At least some overflow futures must be rejected immediately when the queue is full");

      for (CompletableFuture<List<PostingResult>> droppedFuture : droppedFutures) {
        if (droppedFuture.isDone()) {
          List<PostingResult> droppedResults =
              droppedFuture.get(1, java.util.concurrent.TimeUnit.SECONDS);
          assertFalse(
              droppedResults.isEmpty(), "Rejected future must contain at least one failure result");
          assertFalse(
              droppedResults.get(0).success(), "Rejected result must be a failure, not a success");
        }
      }

      // Unblock the worker so the first future and any queued futures can complete.
      releaseWorker.countDown();

      List<PostingResult> firstResults = firstFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);
      assertNotNull(firstResults, "First future must resolve after the worker is unblocked");
      assertEquals(1, firstResults.size());
      assertTrue(firstResults.get(0).success(), "First exception must be processed successfully");
    } finally {
      releaseWorker.countDown(); // idempotent — safe to call twice
      obs.shutdown();
    }
  }

  /**
   * After shutdown(), calling captureException() must not throw on the calling thread and the
   * returned future must complete immediately with a failure result.
   */
  @Test
  void captureException_afterShutdown_doesNotThrow() throws Exception {
    LatchedPostingService svc = new LatchedPostingService();
    Observarium obs = Observarium.builder().addPostingService(svc).build();

    obs.shutdown();

    // captureException must return without throwing; the event is silently dropped.
    CompletableFuture<List<PostingResult>> future =
        obs.captureException(new RuntimeException("post-shutdown"));

    assertTrue(future.isDone(), "Future must be completed immediately after shutdown rejection");
    List<PostingResult> results = future.get(1, java.util.concurrent.TimeUnit.SECONDS);
    assertFalse(results.isEmpty(), "Must contain at least one failure result");
    assertFalse(results.get(0).success(), "Result must be a failure after shutdown");
  }

  /**
   * An exception submitted just before shutdown() must still be fully processed. The Observarium
   * javadoc states that already-queued work continues to be processed after shutdown; this test
   * verifies that contract holds in practice.
   *
   * <p>Two latches coordinate the test:
   *
   * <ul>
   *   <li>{@code workerEntered} — counted down by the stub as soon as it enters createIssue, giving
   *       the test a reliable signal that the worker is in-flight and blocked.
   *   <li>{@code releaseWorker} — counted down by the test after calling shutdown(), allowing the
   *       worker to complete so the future can be resolved.
   * </ul>
   */
  @Test
  void shutdown_waitsForInFlightCaptures() throws Exception {
    CountDownLatch workerEntered = new CountDownLatch(1);
    CountDownLatch releaseWorker = new CountDownLatch(1);
    LatchedPostingService svc = new LatchedPostingService(workerEntered, releaseWorker);
    Observarium obs = Observarium.builder().addPostingService(svc).build();

    // Submit the exception — the worker thread will pick it up and block on releaseWorker after
    // signalling workerEntered.
    CompletableFuture<List<PostingResult>> future =
        obs.captureException(new RuntimeException("in-flight"));

    // Wait until the worker has entered createIssue and is genuinely in-flight.
    assertTrue(
        workerEntered.await(5, java.util.concurrent.TimeUnit.SECONDS),
        "Worker must enter createIssue within 5 s");

    // Shutdown while the worker is mid-task. A well-behaved executor must not abort it.
    obs.shutdown();

    // Now unblock the worker so it can complete the task and resolve the future.
    releaseWorker.countDown();

    // The future must complete successfully — the in-flight task was not abandoned.
    List<PostingResult> results = future.get(5, java.util.concurrent.TimeUnit.SECONDS);

    assertNotNull(results, "Future must complete with a non-null result after shutdown");
    assertEquals(1, results.size());
    assertTrue(results.get(0).success(), "In-flight exception must be processed to completion");
    assertEquals(
        1, svc.createIssueCallCount.get(), "createIssue must have been called exactly once");
  }
}
