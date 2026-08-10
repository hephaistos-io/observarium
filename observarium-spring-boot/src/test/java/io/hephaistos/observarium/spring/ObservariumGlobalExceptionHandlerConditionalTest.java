package io.hephaistos.observarium.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Tests for the acceptance criteria of issue #14: {@link ObservariumGlobalExceptionHandler} must
 * not register when the application already defines its own catch-all {@code @ControllerAdvice},
 * must register and capture when it does not, and {@code observarium.mvc.advice-enabled=false} must
 * suppress it in both cases.
 */
class ObservariumGlobalExceptionHandlerConditionalTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ObservariumAutoConfiguration.class));

  @Test
  void withNoApplicationAdvice_observariumGlobalExceptionHandlerIsRegistered() {
    runner.run(
        context -> assertThat(context).hasSingleBean(ObservariumGlobalExceptionHandler.class));
  }

  @Test
  void withNoApplicationAdvice_thrownExceptionReachesStubPostingService()
      throws InterruptedException {
    List<ExceptionEvent> captured = new CopyOnWriteArrayList<>();

    runner
        .withBean(
            "stubPostingService", PostingService.class, () -> recordingPostingService(captured))
        .run(
            context -> {
              assertThat(context).hasSingleBean(ObservariumGlobalExceptionHandler.class);
              ObservariumGlobalExceptionHandler handler =
                  context.getBean(ObservariumGlobalExceptionHandler.class);

              RuntimeException ex = new RuntimeException("boom");
              assertThatThrownBy(() -> handler.handleException(ex)).isSameAs(ex);

              awaitCapture(captured);
              assertThat(captured).hasSize(1);
              assertThat(captured.get(0).message()).isEqualTo("boom");
            });
  }

  @Test
  void withApplicationDefinedCatchAllAdvice_observariumGlobalExceptionHandlerIsNotRegistered() {
    runner
        .withUserConfiguration(ApplicationOwnedAdviceConfig.class)
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(ObservariumGlobalExceptionHandler.class);
              assertThat(context).hasSingleBean(ApplicationCatchAllAdvice.class);
            });
  }

  @Test
  void adviceEnabledFalse_suppressesRegistration_withoutApplicationAdvice() {
    runner
        .withPropertyValues("observarium.mvc.advice-enabled=false")
        .run(
            context ->
                assertThat(context).doesNotHaveBean(ObservariumGlobalExceptionHandler.class));
  }

  @Test
  void adviceEnabledFalse_suppressesRegistration_withApplicationAdvice() {
    runner
        .withUserConfiguration(ApplicationOwnedAdviceConfig.class)
        .withPropertyValues("observarium.mvc.advice-enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(ObservariumGlobalExceptionHandler.class);
              assertThat(context).hasSingleBean(ApplicationCatchAllAdvice.class);
            });
  }

  private void awaitCapture(List<ExceptionEvent> captured) throws InterruptedException {
    long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
    while (captured.isEmpty() && System.currentTimeMillis() < deadline) {
      Thread.sleep(20);
    }
  }

  private PostingService recordingPostingService(List<ExceptionEvent> sink) {
    return new PostingService() {
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
        sink.add(event);
        return PostingResult.success("ISSUE-1", "https://tracker/ISSUE-1");
      }

      @Override
      public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
        return PostingResult.success(externalIssueId, "https://tracker/" + externalIssueId);
      }
    };
  }

  /** A minimal application-owned catch-all advice, standing in for a real host application's. */
  @Configuration
  static class ApplicationOwnedAdviceConfig {
    @Bean
    ApplicationCatchAllAdvice applicationCatchAllAdvice() {
      return new ApplicationCatchAllAdvice();
    }
  }

  @ControllerAdvice
  static class ApplicationCatchAllAdvice {
    @ExceptionHandler(Exception.class)
    void handle(Exception ex) {
      // application's own error contract, irrelevant to this test
    }
  }
}
