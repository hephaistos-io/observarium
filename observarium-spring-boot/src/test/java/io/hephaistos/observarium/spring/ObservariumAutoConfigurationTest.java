package io.hephaistos.observarium.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.ObservariumListener;
import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.event.Severity;
import io.hephaistos.observarium.fingerprint.ExceptionFingerprinter;
import io.hephaistos.observarium.handler.ObservariumExceptionHandler;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.scrub.DataScrubber;
import io.hephaistos.observarium.scrub.ScrubLevel;
import io.hephaistos.observarium.trace.TraceContextProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tests for {@link ObservariumAutoConfiguration}.
 *
 * <p>Uses {@link ApplicationContextRunner} to exercise the auto-configuration in isolation without
 * starting a full Spring Boot application.
 */
class ObservariumAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ObservariumAutoConfiguration.class));

  @Test
  void observariumBeanIsCreatedWithDefaultProperties() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(Observarium.class);
          assertThat(context).hasSingleBean(ExceptionFingerprinter.class);
          assertThat(context).hasSingleBean(DataScrubber.class);
          assertThat(context).hasSingleBean(TraceContextProvider.class);
          assertThat(context).hasSingleBean(ObservariumExceptionHandler.class);
        });
  }

  @Test
  void observariumIsDisabledWhenPropertySetToFalse() {
    runner
        .withPropertyValues("observarium.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(Observarium.class);
            });
  }

  @Test
  void defaultScrubLevelIsBasic() {
    runner.run(
        context -> {
          ObservariumProperties properties = context.getBean(ObservariumProperties.class);
          assertThat(properties.getScrubLevel()).isEqualTo(ScrubLevel.BASIC);
        });
  }

  @Test
  void scrubLevelCanBeOverriddenViaProperties() {
    runner
        .withPropertyValues("observarium.scrub-level=STRICT")
        .run(
            context -> {
              ObservariumProperties properties = context.getBean(ObservariumProperties.class);
              assertThat(properties.getScrubLevel()).isEqualTo(ScrubLevel.STRICT);
            });
  }

  @Test
  void defaultMdcKeysAreConfigured() {
    runner.run(
        context -> {
          ObservariumProperties properties = context.getBean(ObservariumProperties.class);
          assertThat(properties.getTraceIdMdcKey()).isEqualTo("trace_id");
          assertThat(properties.getSpanIdMdcKey()).isEqualTo("span_id");
        });
  }

  @Test
  void mdcKeysCanBeOverriddenViaProperties() {
    runner
        .withPropertyValues(
            "observarium.trace-id-mdc-key=traceId", "observarium.span-id-mdc-key=spanId")
        .run(
            context -> {
              ObservariumProperties properties = context.getBean(ObservariumProperties.class);
              assertThat(properties.getTraceIdMdcKey()).isEqualTo("traceId");
              assertThat(properties.getSpanIdMdcKey()).isEqualTo("spanId");
            });
  }

  @Test
  void userDefinedObservariumBeanTakesPrecedence() {
    Observarium customObservarium = Observarium.builder().build();
    runner
        .withBean(Observarium.class, () -> customObservarium)
        .run(
            context -> {
              assertThat(context).hasSingleBean(Observarium.class);
              assertThat(context.getBean(Observarium.class)).isSameAs(customObservarium);
            });
  }

  // -----------------------------------------------------------------------
  // ObservariumListener bean wiring
  // -----------------------------------------------------------------------

  static class RecordingListener implements ObservariumListener {
    volatile boolean captured;

    @Override
    public void onExceptionCaptured(Severity severity) {
      captured = true;
    }
  }

  @Configuration
  static class RecordingListenerConfig {
    @Bean
    RecordingListener recordingListener() {
      return new RecordingListener();
    }
  }

  @Test
  void userDefinedListenerBean_isRegisteredOnTheObservariumInstance() throws Exception {
    runner
        .withUserConfiguration(RecordingListenerConfig.class)
        .run(
            context -> {
              Observarium observarium = context.getBean(Observarium.class);
              RecordingListener listener = context.getBean(RecordingListener.class);

              observarium.captureException(new RuntimeException("e")).get();

              assertThat(listener.captured).isTrue();
            });
  }

  // -----------------------------------------------------------------------
  // observarium.queue-capacity (#19)
  // -----------------------------------------------------------------------

  /** Posting service whose findDuplicate() blocks until released, to observe queue backpressure. */
  static class BlockingPostingService implements PostingService {
    final CountDownLatch entered = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);

    @Override
    public String name() {
      return "blocking";
    }

    @Override
    public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
      entered.countDown();
      try {
        release.await(10, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      return DuplicateSearchResult.notFound();
    }

    @Override
    public PostingResult createIssue(ExceptionEvent event) {
      return PostingResult.success("X", "https://tracker/X");
    }

    @Override
    public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
      return PostingResult.success(externalIssueId, "https://tracker/" + externalIssueId);
    }
  }

  @Configuration
  static class BlockingPostingServiceConfig {
    @Bean
    BlockingPostingService blockingPostingService() {
      return new BlockingPostingService();
    }
  }

  @Test
  void queueCapacityProperty_boundsTheBackingQueue() throws Exception {
    runner
        .withUserConfiguration(BlockingPostingServiceConfig.class)
        .withPropertyValues("observarium.queue-capacity=1")
        .run(
            context -> {
              Observarium observarium = context.getBean(Observarium.class);
              BlockingPostingService blocking = context.getBean(BlockingPostingService.class);
              try {
                // First capture occupies the single worker thread.
                observarium.captureException(new RuntimeException("in-flight"));
                assertThat(blocking.entered.await(5, TimeUnit.SECONDS)).isTrue();
                // Second capture fills the queue slot (capacity 1).
                observarium.captureException(new RuntimeException("queued"));
                // Third capture must be dropped: queue-capacity=1 leaves no room.
                List<PostingResult> dropped =
                    observarium.captureException(new RuntimeException("dropped")).get();
                assertThat(dropped).hasSize(1);
                assertThat(dropped.get(0).success()).isFalse();
              } finally {
                blocking.release.countDown();
              }
            });
  }

  // -----------------------------------------------------------------------
  // observarium.scrub-patterns (#19)
  // -----------------------------------------------------------------------

  static class RecordingPostingService implements PostingService {
    final List<ExceptionEvent> received = new ArrayList<>();

    @Override
    public String name() {
      return "recording";
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

  @Configuration
  static class RecordingPostingServiceConfig {
    @Bean
    RecordingPostingService recordingPostingService() {
      return new RecordingPostingService();
    }
  }

  @Test
  void scrubPatternsProperty_isForwardedToTheScrubber() throws Exception {
    runner
        .withUserConfiguration(RecordingPostingServiceConfig.class)
        .withPropertyValues("observarium.scrub-patterns[0]=ORD-\\d+")
        .run(
            context -> {
              Observarium observarium = context.getBean(Observarium.class);
              RecordingPostingService recorder = context.getBean(RecordingPostingService.class);

              observarium.captureException(new RuntimeException("order ORD-4471 failed")).get();

              assertThat(recorder.received).hasSize(1);
              assertThat(recorder.received.get(0).message()).doesNotContain("ORD-4471");
            });
  }

  @Test
  void invalidScrubPattern_failsStartupNamingThePattern() {
    runner
        .withPropertyValues("observarium.scrub-patterns[0]=[unterminated")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure()).hasStackTraceContaining("[unterminated");
            });
  }
}
