package io.hephaistos.observarium;

import static org.assertj.core.api.Assertions.assertThat;

import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.event.Severity;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ObservariumListenerTest {

  private Observarium observarium;

  @AfterEach
  void tearDown() {
    if (observarium != null) {
      observarium.shutdown();
    }
  }

  @Test
  void listener_receivesOnExceptionCaptured() throws Exception {
    RecordingListener listener = new RecordingListener();
    var latch = new CountDownLatch(1);
    observarium =
        Observarium.builder()
            .listener(listener)
            .addPostingService(latchService("svc", latch))
            .build();

    observarium.captureException(new RuntimeException("test"), Severity.WARNING);
    latch.await(5, TimeUnit.SECONDS);

    assertThat(listener.capturedSeverities).containsExactly(Severity.WARNING);
  }

  @Test
  void listener_receivesOnExceptionDropped_whenQueueFull() throws Exception {
    RecordingListener listener = new RecordingListener();
    var blockingLatch = new CountDownLatch(1);
    var droppedLatch = new CountDownLatch(1);

    // Queue capacity 1 + 1 worker thread = 2 can be in flight at most
    observarium =
        Observarium.builder()
            .listener(
                new ObservariumListener() {
                  @Override
                  public void onExceptionCaptured(Severity severity) {
                    listener.onExceptionCaptured(severity);
                  }

                  @Override
                  public void onExceptionDropped() {
                    listener.onExceptionDropped();
                    droppedLatch.countDown();
                  }

                  @Override
                  public void onPostingCompleted(
                      String serviceName, boolean duplicate, boolean success, long durationNanos) {
                    listener.onPostingCompleted(serviceName, duplicate, success, durationNanos);
                  }
                })
            .queueCapacity(1)
            .addPostingService(blockingService("svc", blockingLatch))
            .build();

    // First exception blocks the worker thread
    observarium.captureException(new RuntimeException("first"));
    // Second fills the queue
    observarium.captureException(new RuntimeException("second"));
    // Third should be dropped
    observarium.captureException(new RuntimeException("third"));

    assertThat(droppedLatch.await(5, TimeUnit.SECONDS))
        .as("onExceptionDropped should be called")
        .isTrue();
    assertThat(listener.droppedCount.get()).isGreaterThanOrEqualTo(1);

    blockingLatch.countDown();
  }

  @Test
  void listener_receivesOnPostingCompleted() throws Exception {
    RecordingListener listener = new RecordingListener();
    var latch = new CountDownLatch(1);
    observarium =
        Observarium.builder()
            .listener(listener)
            .addPostingService(latchService("github", latch))
            .build();

    observarium.captureException(new RuntimeException("test"));
    latch.await(5, TimeUnit.SECONDS);

    assertThat(listener.postingCompletions).hasSize(1);
    RecordingListener.PostingCompletion completion = listener.postingCompletions.get(0);
    assertThat(completion.serviceName).isEqualTo("github");
    assertThat(completion.success).isTrue();
    assertThat(completion.duplicate).isFalse();
    assertThat(completion.durationNanos).isGreaterThan(0);
  }

  @Test
  void listener_receivesOnQueueSizeAvailable() {
    RecordingListener listener = new RecordingListener();
    observarium = Observarium.builder().listener(listener).build();

    assertThat(listener.queueSizeSupplier).isNotNull();
    assertThat(listener.queueSizeSupplier.get()).isEqualTo(0);
  }

  @Test
  void listener_exceptionInCallback_doesNotDisruptPipeline() throws Exception {
    var latch = new CountDownLatch(1);
    observarium =
        Observarium.builder()
            .listener(
                new ObservariumListener() {
                  @Override
                  public void onExceptionCaptured(Severity severity) {
                    throw new RuntimeException("listener failure");
                  }
                })
            .addPostingService(latchService("svc", latch))
            .build();

    var future = observarium.captureException(new RuntimeException("test"));
    latch.await(5, TimeUnit.SECONDS);

    var results = future.get(5, TimeUnit.SECONDS);
    assertThat(results).hasSize(1);
    assertThat(results.get(0).success()).isTrue();
  }

  @Test
  void listener_receivesOnCommentDropped_whenLimitExceeded() throws Exception {
    RecordingListener listener = new RecordingListener();
    var completedLatch = new CountDownLatch(1);
    observarium =
        Observarium.builder()
            .maxDuplicateComments(5)
            .listener(
                new ObservariumListener() {
                  @Override
                  public void onCommentDropped(String serviceName) {
                    listener.onCommentDropped(serviceName);
                  }

                  @Override
                  public void onPostingCompleted(
                      String serviceName, boolean duplicate, boolean success, long durationNanos) {
                    listener.onPostingCompleted(serviceName, duplicate, success, durationNanos);
                    completedLatch.countDown();
                  }
                })
            .addPostingService(commentLimitExceededService("tracker"))
            .build();

    observarium.captureException(new RuntimeException("test")).get(5, TimeUnit.SECONDS);
    assertThat(completedLatch.await(5, TimeUnit.SECONDS))
        .as("onPostingCompleted should be called")
        .isTrue();

    assertThat(listener.commentDroppedServiceNames).containsExactly("tracker");
  }

  // ---------------------------------------------------------------------------
  // Recording listener
  // ---------------------------------------------------------------------------

  private static class RecordingListener implements ObservariumListener {

    final List<Severity> capturedSeverities = new CopyOnWriteArrayList<>();
    final AtomicInteger droppedCount = new AtomicInteger();
    final List<PostingCompletion> postingCompletions = new CopyOnWriteArrayList<>();
    final List<String> commentDroppedServiceNames = new CopyOnWriteArrayList<>();
    volatile Supplier<Integer> queueSizeSupplier;

    @Override
    public void onExceptionCaptured(Severity severity) {
      capturedSeverities.add(severity);
    }

    @Override
    public void onExceptionDropped() {
      droppedCount.incrementAndGet();
    }

    @Override
    public void onPostingCompleted(
        String serviceName, boolean duplicate, boolean success, long durationNanos) {
      postingCompletions.add(new PostingCompletion(serviceName, duplicate, success, durationNanos));
    }

    @Override
    public void onCommentDropped(String serviceName) {
      commentDroppedServiceNames.add(serviceName);
    }

    @Override
    public void onQueueSizeAvailable(Supplier<Integer> supplier) {
      this.queueSizeSupplier = supplier;
    }

    record PostingCompletion(
        String serviceName, boolean duplicate, boolean success, long durationNanos) {}
  }

  // ---------------------------------------------------------------------------
  // Stub posting services
  // ---------------------------------------------------------------------------

  private static PostingService latchService(String name, CountDownLatch completionLatch) {
    return new PostingService() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
        return DuplicateSearchResult.notFound();
      }

      @Override
      public PostingResult createIssue(ExceptionEvent event) {
        completionLatch.countDown();
        return PostingResult.success("ISSUE-1", "https://tracker/ISSUE-1");
      }

      @Override
      public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
        return PostingResult.success(externalIssueId, "https://tracker/" + externalIssueId);
      }
    };
  }

  private static PostingService blockingService(String name, CountDownLatch blockLatch) {
    return new PostingService() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
        try {
          blockLatch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        return DuplicateSearchResult.notFound();
      }

      @Override
      public PostingResult createIssue(ExceptionEvent event) {
        return PostingResult.success("ISSUE-1", "https://tracker/ISSUE-1");
      }

      @Override
      public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
        return PostingResult.success(externalIssueId, "https://tracker/" + externalIssueId);
      }
    };
  }

  private static PostingService commentLimitExceededService(String name) {
    return new PostingService() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
        // Return a duplicate with a comment count that exceeds the configured limit of 5
        return DuplicateSearchResult.found("ISSUE-1", "https://tracker/ISSUE-1", 10);
      }

      @Override
      public PostingResult createIssue(ExceptionEvent event) {
        return PostingResult.success("ISSUE-1", "https://tracker/ISSUE-1");
      }

      @Override
      public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
        return PostingResult.success(externalIssueId, "https://tracker/" + externalIssueId);
      }
    };
  }
}
