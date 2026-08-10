package io.hephaistos.observarium.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.handler.ObservariumExceptionHandler;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * Integration tests verifying that {@link ObservariumAutoConfiguration} installs {@link
 * ObservariumExceptionHandler} as the JVM default uncaught exception handler and that the handler
 * correctly routes uncaught exceptions through {@link Observarium}.
 *
 * <p>Each test saves and restores the JVM default uncaught exception handler so that tests do not
 * pollute each other or the surrounding test suite.
 */
class UncaughtExceptionHandlerIntegrationTest {

  private Thread.UncaughtExceptionHandler savedDefaultHandler;

  @BeforeEach
  void saveDefaultHandler() {
    savedDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
  }

  @AfterEach
  void restoreDefaultHandler() {
    Thread.setDefaultUncaughtExceptionHandler(savedDefaultHandler);
  }

  // -----------------------------------------------------------------------
  // Handler registration via auto-configuration
  // -----------------------------------------------------------------------

  @Test
  void byDefault_handlerBeanIsNotCreatedAndJvmDefaultIsUnchanged() {
    Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();

    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ObservariumAutoConfiguration.class))
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(ObservariumExceptionHandler.class);
              assertThat(Thread.getDefaultUncaughtExceptionHandler()).isSameAs(before);
            });
  }

  @Test
  void whenInstallUncaughtHandlerEnabled_autoConfigurationInstallsHandlerAsJvmDefault() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ObservariumAutoConfiguration.class))
        .withPropertyValues("observarium.install-uncaught-handler=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(ObservariumExceptionHandler.class);

              Thread.UncaughtExceptionHandler installed =
                  Thread.getDefaultUncaughtExceptionHandler();
              assertThat(installed)
                  .isInstanceOf(ObservariumExceptionHandler.class)
                  .isSameAs(context.getBean(ObservariumExceptionHandler.class));
            });
  }

  @Test
  void whenObservariumDisabled_handlerBeanIsNotCreatedAndJvmDefaultIsUnchanged() {
    Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();

    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ObservariumAutoConfiguration.class))
        .withPropertyValues(
            "observarium.enabled=false", "observarium.install-uncaught-handler=true")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(ObservariumExceptionHandler.class);
              assertThat(Thread.getDefaultUncaughtExceptionHandler()).isSameAs(before);
            });
  }

  @Test
  void whenInstallUncaughtHandlerExplicitlyFalse_handlerIsNotInstalled() {
    Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();

    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ObservariumAutoConfiguration.class))
        .withPropertyValues("observarium.install-uncaught-handler=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(ObservariumExceptionHandler.class);
              assertThat(Thread.getDefaultUncaughtExceptionHandler()).isSameAs(before);
            });
  }

  // -----------------------------------------------------------------------
  // Restoration on context close
  // -----------------------------------------------------------------------

  /**
   * Builds and refreshes an {@link AnnotationConfigApplicationContext} with {@link
   * ObservariumAutoConfiguration} registered and {@code observarium.install-uncaught-handler}
   * forced to {@code true}. Using a raw context (rather than {@link ApplicationContextRunner}) lets
   * the tests below observe the JVM default handler both while the context is open and after it is
   * closed, which the runner's callback-scoped {@code run()} does not support.
   */
  private AnnotationConfigApplicationContext contextWithHandlerInstalled() {
    var context = new AnnotationConfigApplicationContext();
    context.register(ObservariumAutoConfiguration.class);
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource("test", Map.of("observarium.install-uncaught-handler", "true")));
    context.refresh();
    return context;
  }

  @Test
  void closingContext_restoresThePreviousDefaultHandler() {
    Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();

    try (var context = contextWithHandlerInstalled()) {
      assertThat(Thread.getDefaultUncaughtExceptionHandler())
          .isSameAs(context.getBean(ObservariumExceptionHandler.class));

      context.close();

      assertThat(Thread.getDefaultUncaughtExceptionHandler()).isSameAs(before);
    }
  }

  @Test
  void buildingAndClosingManyContextsInSequence_leavesNoHandlerChain() {
    Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();

    for (int i = 0; i < 5; i++) {
      try (var context = contextWithHandlerInstalled()) {
        assertThat(Thread.getDefaultUncaughtExceptionHandler())
            .isInstanceOf(ObservariumExceptionHandler.class);
      }
    }

    // No chain of delegates from closed contexts should remain: the default handler after the
    // last context closes must be exactly what it was before the very first context started.
    assertThat(Thread.getDefaultUncaughtExceptionHandler()).isSameAs(before);
  }

  @Test
  void whenSomethingElseReplacesTheHandler_contextCloseDoesNotClobberIt() {
    Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();

    try (var context = contextWithHandlerInstalled()) {
      Thread.UncaughtExceptionHandler somethingElse = (thread, ex) -> {};
      Thread.setDefaultUncaughtExceptionHandler(somethingElse);

      context.close();

      assertThat(Thread.getDefaultUncaughtExceptionHandler()).isSameAs(somethingElse);
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(before);
    }
  }

  // -----------------------------------------------------------------------
  // End-to-end: uncaught exception is delivered to Observarium
  // -----------------------------------------------------------------------

  /**
   * Spawns a thread that throws an uncaught exception and verifies that the event is delivered to
   * the configured {@link PostingService}.
   *
   * <p>A {@link CountDownLatch} synchronises the test thread with Observarium's async worker so
   * that assertions are not made before delivery has completed.
   */
  @Test
  void uncaughtExceptionFromSpawnedThread_isDeliveredToPostingService()
      throws InterruptedException {
    CountDownLatch deliveredLatch = new CountDownLatch(1);
    List<ExceptionEvent> receivedEvents = new ArrayList<>();

    PostingService recorder =
        new PostingService() {
          @Override
          public String name() {
            return "recorder";
          }

          @Override
          public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
            return DuplicateSearchResult.notFound();
          }

          @Override
          public PostingResult createIssue(ExceptionEvent event) {
            receivedEvents.add(event);
            deliveredLatch.countDown();
            return PostingResult.success("ISSUE-1", "https://tracker/ISSUE-1");
          }

          @Override
          public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
            return PostingResult.success(externalIssueId, "https://tracker/" + externalIssueId);
          }
        };

    Observarium observarium = Observarium.builder().addPostingService(recorder).build();
    try {
      ObservariumExceptionHandler handler =
          new ObservariumExceptionHandler(observarium, savedDefaultHandler);
      Thread.setDefaultUncaughtExceptionHandler(handler);

      AtomicBoolean threadStarted = new AtomicBoolean(false);
      Thread faultyThread =
          new Thread(
              () -> {
                threadStarted.set(true);
                throw new RuntimeException("uncaught-test-exception");
              },
              "faulty-thread");

      faultyThread.start();
      faultyThread.join(5_000);

      boolean delivered = deliveredLatch.await(10, TimeUnit.SECONDS);

      assertThat(threadStarted).as("faulty thread must have started").isTrue();
      assertThat(delivered)
          .as("PostingService.createIssue must be called within 10 s after the uncaught exception")
          .isTrue();
      assertThat(receivedEvents).hasSize(1);
      assertThat(receivedEvents.get(0).message()).isEqualTo("uncaught-test-exception");
    } finally {
      observarium.shutdown();
    }
  }
}
