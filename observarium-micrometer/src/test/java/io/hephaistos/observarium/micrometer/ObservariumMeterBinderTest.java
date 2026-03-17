package io.hephaistos.observarium.micrometer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.hephaistos.observarium.event.Severity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ObservariumMeterBinderTest {

  private MeterRegistry registry;
  private ObservariumMeterBinder binder;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    binder = new ObservariumMeterBinder();
    binder.bindTo(registry);
  }

  // ---------- onExceptionCaptured ----------

  @Test
  void onExceptionCaptured_incrementsCounterWithSeverityTag() {
    binder.onExceptionCaptured(Severity.ERROR);
    binder.onExceptionCaptured(Severity.ERROR);
    binder.onExceptionCaptured(Severity.WARNING);

    Counter errorCounter =
        registry.find("observarium.exceptions.captured").tag("severity", "error").counter();
    Counter warningCounter =
        registry.find("observarium.exceptions.captured").tag("severity", "warning").counter();

    assertThat(errorCounter).isNotNull();
    assertThat(errorCounter.count()).isEqualTo(2.0);
    assertThat(warningCounter).isNotNull();
    assertThat(warningCounter.count()).isEqualTo(1.0);
  }

  @Test
  void onExceptionCaptured_beforeBindTo_doesNotThrow() {
    ObservariumMeterBinder unboundBinder = new ObservariumMeterBinder();
    assertThatNoException().isThrownBy(() -> unboundBinder.onExceptionCaptured(Severity.ERROR));
  }

  // ---------- onExceptionDropped ----------

  @Test
  void onExceptionDropped_incrementsDroppedCounter() {
    binder.onExceptionDropped();
    binder.onExceptionDropped();

    Counter counter = registry.find("observarium.exceptions.dropped").counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(2.0);
  }

  // ---------- onCommentDropped ----------

  @Test
  void onCommentDropped_incrementsCounter() {
    binder.onCommentDropped("github");

    Counter counter =
        registry.find("observarium.comments.dropped").tag("service", "github").counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);

    binder.onCommentDropped("github");
    assertThat(counter.count()).isEqualTo(2.0);
  }

  @Test
  void onCommentDropped_beforeBindTo_doesNotThrow() {
    ObservariumMeterBinder unboundBinder = new ObservariumMeterBinder();
    assertThatNoException().isThrownBy(() -> unboundBinder.onCommentDropped("github"));
  }

  // ---------- onPostingCompleted ----------

  @Test
  void onPostingCompleted_recordsTimerForNewIssue() {
    binder.onPostingCompleted("github", false, true, TimeUnit.MILLISECONDS.toNanos(150));

    Timer timer =
        registry
            .find("observarium.posting.duration")
            .tag("service", "github")
            .tag("action", "create")
            .tag("outcome", "success")
            .timer();

    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS))
        .isCloseTo(150.0, org.assertj.core.data.Offset.offset(1.0));
  }

  @Test
  void onPostingCompleted_recordsTimerForDuplicateComment() {
    binder.onPostingCompleted("jira", true, true, TimeUnit.MILLISECONDS.toNanos(200));

    Timer timer =
        registry
            .find("observarium.posting.duration")
            .tag("service", "jira")
            .tag("action", "comment")
            .tag("outcome", "success")
            .timer();

    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1);
  }

  @Test
  void onPostingCompleted_recordsFailure() {
    binder.onPostingCompleted("gitlab", false, false, TimeUnit.MILLISECONDS.toNanos(50));

    Timer timer =
        registry
            .find("observarium.posting.duration")
            .tag("service", "gitlab")
            .tag("action", "create")
            .tag("outcome", "failure")
            .timer();

    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1);
  }

  @Test
  void onPostingCompleted_multipleCallsAccumulate() {
    binder.onPostingCompleted("github", false, true, TimeUnit.MILLISECONDS.toNanos(100));
    binder.onPostingCompleted("github", false, true, TimeUnit.MILLISECONDS.toNanos(200));

    Timer timer =
        registry
            .find("observarium.posting.duration")
            .tag("service", "github")
            .tag("action", "create")
            .tag("outcome", "success")
            .timer();

    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(2);
    assertThat(timer.totalTime(TimeUnit.MILLISECONDS))
        .isCloseTo(300.0, org.assertj.core.data.Offset.offset(1.0));
  }

  // ---------- onQueueSizeAvailable ----------

  @Test
  void onQueueSizeAvailable_registersGauge() {
    AtomicInteger queueSize = new AtomicInteger(5);
    binder.onQueueSizeAvailable(queueSize::get);

    Gauge gauge = registry.find("observarium.queue.size").gauge();
    assertThat(gauge).isNotNull();
    assertThat(gauge.value()).isEqualTo(5.0);

    queueSize.set(12);
    assertThat(gauge.value()).isEqualTo(12.0);
  }

  @Test
  void onQueueSizeAvailable_beforeBindTo_gaugeRegisteredOnBind() {
    ObservariumMeterBinder earlyBinder = new ObservariumMeterBinder();
    AtomicInteger queueSize = new AtomicInteger(3);
    earlyBinder.onQueueSizeAvailable(queueSize::get);

    // Gauge should not exist yet
    assertThat(registry.find("observarium.queue.size").gauge()).isNull();

    earlyBinder.bindTo(registry);

    Gauge gauge = registry.find("observarium.queue.size").gauge();
    assertThat(gauge).isNotNull();
    assertThat(gauge.value()).isEqualTo(3.0);
  }

  // ---------- pre-bind safety ----------

  @Test
  void onExceptionDropped_beforeBindTo_doesNotThrow() {
    ObservariumMeterBinder unboundBinder = new ObservariumMeterBinder();
    assertThatNoException().isThrownBy(unboundBinder::onExceptionDropped);
  }

  @Test
  void onPostingCompleted_beforeBindTo_doesNotThrow() {
    ObservariumMeterBinder unboundBinder = new ObservariumMeterBinder();
    assertThatNoException()
        .isThrownBy(
            () ->
                unboundBinder.onPostingCompleted(
                    "github", false, true, TimeUnit.MILLISECONDS.toNanos(100)));
  }

  // ---------- MeterBinder contract ----------

  @Test
  void droppedCounter_isRegisteredOnBind() {
    Counter counter = registry.find("observarium.exceptions.dropped").counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isZero();
  }

  // ---------- close ----------

  @Test
  void close_removesAllMetersFromRegistry() {
    binder.onExceptionCaptured(Severity.ERROR);
    binder.onExceptionDropped();
    binder.onPostingCompleted("github", false, true, TimeUnit.MILLISECONDS.toNanos(100));
    binder.onQueueSizeAvailable(new AtomicInteger(5)::get);

    // Verify meters exist
    assertThat(registry.find("observarium.exceptions.captured").counter()).isNotNull();
    assertThat(registry.find("observarium.exceptions.dropped").counter()).isNotNull();
    assertThat(registry.find("observarium.posting.duration").timer()).isNotNull();
    assertThat(registry.find("observarium.queue.size").gauge()).isNotNull();

    binder.close();

    // All meters should be removed
    assertThat(registry.find("observarium.exceptions.captured").counter()).isNull();
    assertThat(registry.find("observarium.exceptions.dropped").counter()).isNull();
    assertThat(registry.find("observarium.posting.duration").timer()).isNull();
    assertThat(registry.find("observarium.queue.size").gauge()).isNull();
  }

  @Test
  void close_removesCommentDroppedCounters() {
    binder.onCommentDropped("github");

    assertThat(registry.find("observarium.comments.dropped").tag("service", "github").counter())
        .isNotNull();

    binder.close();

    assertThat(registry.find("observarium.comments.dropped").tag("service", "github").counter())
        .isNull();
  }

  @Test
  void close_callbacksIgnoredAfterClose() {
    binder.close();

    assertThatNoException().isThrownBy(() -> binder.onExceptionCaptured(Severity.ERROR));
    assertThatNoException().isThrownBy(binder::onExceptionDropped);
    assertThatNoException()
        .isThrownBy(
            () ->
                binder.onPostingCompleted(
                    "github", false, true, TimeUnit.MILLISECONDS.toNanos(100)));
  }

  @Test
  void close_idempotent() {
    assertThatNoException()
        .isThrownBy(
            () -> {
              binder.close();
              binder.close();
            });
  }

  // ---------- common tags ----------

  @Test
  void commonTags_appliedToAllMeters() {
    MeterRegistry tagRegistry = new SimpleMeterRegistry();
    ObservariumMeterBinder taggedBinder =
        new ObservariumMeterBinder(Tags.of("app", "myapp", "env", "prod"));
    taggedBinder.bindTo(tagRegistry);

    taggedBinder.onExceptionCaptured(Severity.ERROR);
    taggedBinder.onExceptionDropped();
    taggedBinder.onPostingCompleted("github", false, true, TimeUnit.MILLISECONDS.toNanos(100));
    taggedBinder.onQueueSizeAvailable(new AtomicInteger(1)::get);

    assertThat(
            tagRegistry
                .find("observarium.exceptions.captured")
                .tag("app", "myapp")
                .tag("env", "prod")
                .counter())
        .isNotNull();

    assertThat(
            tagRegistry
                .find("observarium.exceptions.dropped")
                .tag("app", "myapp")
                .tag("env", "prod")
                .counter())
        .isNotNull();

    assertThat(
            tagRegistry
                .find("observarium.posting.duration")
                .tag("app", "myapp")
                .tag("env", "prod")
                .timer())
        .isNotNull();

    assertThat(
            tagRegistry
                .find("observarium.queue.size")
                .tag("app", "myapp")
                .tag("env", "prod")
                .gauge())
        .isNotNull();

    taggedBinder.close();
  }

  // ---------- concurrent access ----------

  @Test
  void concurrentCallbacks_doNotCorruptMeters() throws Exception {
    int threadCount = 8;
    int iterationsPerThread = 500;
    ExecutorService pool = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    List<Exception> errors = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      int threadIdx = i;
      pool.submit(
          () -> {
            try {
              startLatch.await(5, TimeUnit.SECONDS);
              for (int j = 0; j < iterationsPerThread; j++) {
                Severity severity = threadIdx % 2 == 0 ? Severity.ERROR : Severity.WARNING;
                binder.onExceptionCaptured(severity);
                binder.onExceptionDropped();
                binder.onPostingCompleted(
                    "service-" + (threadIdx % 3), false, true, TimeUnit.MILLISECONDS.toNanos(10));
              }
            } catch (Exception e) {
              synchronized (errors) {
                errors.add(e);
              }
            }
          });
    }

    startLatch.countDown();
    pool.shutdown();
    assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    assertThat(errors).isEmpty();

    // Verify total counts are consistent
    double totalCaptured = 0;
    for (Counter c : registry.find("observarium.exceptions.captured").counters()) {
      totalCaptured += c.count();
    }
    assertThat(totalCaptured).isEqualTo((double) threadCount * iterationsPerThread);

    Counter dropped = registry.find("observarium.exceptions.dropped").counter();
    assertThat(dropped).isNotNull();
    assertThat(dropped.count()).isEqualTo((double) threadCount * iterationsPerThread);
  }
}
