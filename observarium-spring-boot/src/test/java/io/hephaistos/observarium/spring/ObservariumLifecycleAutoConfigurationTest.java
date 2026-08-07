package io.hephaistos.observarium.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.context.WebServerGracefulShutdownLifecycle;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

/**
 * Context tests for issue #16: {@code Observarium} shutdown is driven by a {@link
 * ObservariumLifecycle} ({@code SmartLifecycle}) sharing a phase with Spring Boot's web server
 * graceful shutdown, instead of a bean {@code destroyMethod} that would run strictly after it.
 */
class ObservariumLifecycleAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ObservariumAutoConfiguration.class));

  @Test
  void observariumLifecycleBeanIsRegistered() {
    runner.run(context -> assertThat(context).hasSingleBean(ObservariumLifecycle.class));
  }

  @Test
  void observariumLifecyclePhase_matchesWebServerGracefulShutdownPhase() {
    runner.run(
        context -> {
          ObservariumLifecycle lifecycle = context.getBean(ObservariumLifecycle.class);
          assertThat(lifecycle.getPhase())
              .isEqualTo(WebServerGracefulShutdownLifecycle.SMART_LIFECYCLE_PHASE);
        });
  }

  @Test
  void observariumBean_hasNoDestroyMethod() {
    // Shutdown must be driven exclusively by the SmartLifecycle bean, not by bean destruction
    // (which runs after every SmartLifecycle bean, including the web server's).
    runner.run(
        context -> {
          ConfigurableApplicationContext source =
              (ConfigurableApplicationContext) context.getSourceApplicationContext();
          RootBeanDefinition definition =
              (RootBeanDefinition) source.getBeanFactory().getBeanDefinition("observarium");
          // "" means the inferred-destroy-method heuristic (which would otherwise detect
          // Observarium#shutdown() by name) is explicitly disabled — not merely unset, which
          // would still resolve to "(inferred)" and call shutdown() during destroySingletons().
          assertThat(definition.getDestroyMethodName()).isEqualTo("");
        });
  }

  /**
   * Posting service that records whether close() was invoked, as a proxy for shutdown having run.
   */
  static class ClosingPostingService implements PostingService, AutoCloseable {
    final AtomicBoolean closed = new AtomicBoolean(false);

    @Override
    public String name() {
      return "closing";
    }

    @Override
    public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
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

    @Override
    public void close() {
      closed.set(true);
    }
  }

  @Configuration
  static class ClosingPostingServiceConfig {
    @Bean
    ClosingPostingService closingPostingService() {
      return new ClosingPostingService();
    }
  }

  @Test
  void stoppingTheLifecycleBean_shutsDownObservarium() {
    runner
        .withUserConfiguration(ClosingPostingServiceConfig.class)
        .run(
            context -> {
              ObservariumLifecycle lifecycle = context.getBean(ObservariumLifecycle.class);
              ClosingPostingService service = context.getBean(ClosingPostingService.class);

              assertThat(lifecycle.isRunning()).isTrue();
              lifecycle.stop();

              assertThat(lifecycle.isRunning()).isFalse();
              assertThat(service.closed.get()).isTrue();
            });
  }

  /**
   * Posting service that blocks findDuplicate() until released — simulates an unresponsive tracker.
   */
  static class UnresponsivePostingService implements PostingService {
    final CountDownLatch entered = new CountDownLatch(1);

    @Override
    public String name() {
      return "unresponsive";
    }

    @Override
    public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
      entered.countDown();
      try {
        Thread.sleep(60_000);
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
  static class UnresponsivePostingServiceConfig {
    @Bean
    UnresponsivePostingService unresponsivePostingService() {
      return new UnresponsivePostingService();
    }
  }

  @Test
  void shutdownTimeoutProperty_boundsTheLifecycleDrain() throws Exception {
    // A real (non-runner-managed) context, so we control close() timing precisely: the graceful
    // shutdown budget must come from observarium.shutdown-timeout, not the 10s core default.
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.register(ObservariumAutoConfiguration.class, UnresponsivePostingServiceConfig.class);
      context
          .getEnvironment()
          .getPropertySources()
          .addFirst(new MapPropertySource("test", Map.of("observarium.shutdown-timeout", "300ms")));
      context.refresh();

      Observarium observarium = context.getBean(Observarium.class);
      UnresponsivePostingService service = context.getBean(UnresponsivePostingService.class);
      observarium.captureException(new RuntimeException("stuck"));
      assertThat(service.entered.await(5, TimeUnit.SECONDS)).isTrue();

      long start = System.nanoTime();
      context.close();
      long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

      assertThat(elapsedMillis)
          .as(
              "context close() must be bounded by observarium.shutdown-timeout, not the 10s default")
          .isLessThan(5_000);
    }
  }
}
