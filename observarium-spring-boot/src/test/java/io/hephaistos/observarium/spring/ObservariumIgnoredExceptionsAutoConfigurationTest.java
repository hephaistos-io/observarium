package io.hephaistos.observarium.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Context tests for issue #18: {@code observarium.ignored-exceptions} and the Spring-default
 * "ignore framework-classified client errors" predicate wired in {@link
 * ObservariumAutoConfiguration#observarium}.
 *
 * <p>These drive {@link Observarium#captureException} directly against the real, fully-configured
 * bean rather than going through {@link ObservariumGlobalExceptionHandler} — the wiring under test
 * lives entirely in the {@code observarium} bean method, so this is a faithful context test of the
 * acceptance criteria without touching the MVC advice this agent does not own.
 */
class ObservariumIgnoredExceptionsAutoConfigurationTest {

  /** Records every event it receives; used to assert whether a throwable was reported at all. */
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

  @ResponseStatus(HttpStatus.NOT_FOUND)
  static class DomainNotFoundException extends RuntimeException {}

  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  static class DomainServerErrorException extends RuntimeException {}

  // Deliberately NOT annotated with @ResponseStatus, so the default client-error predicate never
  // matches these — isolates the observarium.ignored-exceptions property's own subclass matching
  // from the framework-supplied defaults.
  static class DomainErrorException extends RuntimeException {}

  static class DomainErrorSubclassException extends DomainErrorException {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ObservariumAutoConfiguration.class))
          .withUserConfiguration(RecordingPostingServiceConfig.class);

  @Test
  void bindException_isIgnoredByDefault() {
    runner.run(
        context -> {
          Observarium observarium = context.getBean(Observarium.class);
          RecordingPostingService recorder = context.getBean(RecordingPostingService.class);

          List<PostingResult> results =
              observarium.captureException(new BindException(new Object(), "target")).get();

          assertThat(results).isEmpty();
          assertThat(recorder.received).isEmpty();
        });
  }

  @Test
  void responseStatus4xxException_isIgnoredByDefault() {
    runner.run(
        context -> {
          Observarium observarium = context.getBean(Observarium.class);
          RecordingPostingService recorder = context.getBean(RecordingPostingService.class);

          List<PostingResult> results =
              observarium.captureException(new DomainNotFoundException()).get();

          assertThat(results).isEmpty();
          assertThat(recorder.received).isEmpty();
        });
  }

  @Test
  void errorResponseImplementation_withA4xxStatus_isIgnoredByDefault() {
    runner.run(
        context -> {
          Observarium observarium = context.getBean(Observarium.class);
          RecordingPostingService recorder = context.getBean(RecordingPostingService.class);

          // ResponseStatusException implements ErrorResponse.
          List<PostingResult> results =
              observarium
                  .captureException(new ResponseStatusException(HttpStatus.BAD_REQUEST))
                  .get();

          assertThat(results).isEmpty();
          assertThat(recorder.received).isEmpty();
        });
  }

  @Test
  void errorResponseImplementation_with5xxStatus_isStillReported() {
    runner.run(
        context -> {
          Observarium observarium = context.getBean(Observarium.class);
          RecordingPostingService recorder = context.getBean(RecordingPostingService.class);

          List<PostingResult> results =
              observarium
                  .captureException(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR))
                  .get();

          assertThat(results).hasSize(1);
          assertThat(recorder.received).hasSize(1);
        });
  }

  @Test
  void responseStatus5xxException_isStillReported() {
    runner.run(
        context -> {
          Observarium observarium = context.getBean(Observarium.class);
          RecordingPostingService recorder = context.getBean(RecordingPostingService.class);

          List<PostingResult> results =
              observarium.captureException(new DomainServerErrorException()).get();

          assertThat(results).hasSize(1);
          assertThat(recorder.received).hasSize(1);
        });
  }

  @Test
  void arbitraryRuntimeException_isStillReported() {
    runner.run(
        context -> {
          Observarium observarium = context.getBean(Observarium.class);
          RecordingPostingService recorder = context.getBean(RecordingPostingService.class);

          List<PostingResult> results =
              observarium.captureException(new RuntimeException("a real defect")).get();

          assertThat(results).hasSize(1);
          assertThat(results.get(0).success()).isTrue();
          assertThat(recorder.received).hasSize(1);
        });
  }

  @Test
  void ignoredExceptionsProperty_matchesConfiguredFqcnAndSubclasses() {
    runner
        .withPropertyValues(
            "observarium.ignored-exceptions[0]=" + ExecutionException.class.getName())
        .run(
            context -> {
              Observarium observarium = context.getBean(Observarium.class);
              RecordingPostingService recorder = context.getBean(RecordingPostingService.class);

              List<PostingResult> exactMatch =
                  observarium.captureException(new ExecutionException("wrapped", null)).get();
              assertThat(exactMatch).isEmpty();

              List<PostingResult> stillReported =
                  observarium.captureException(new IllegalStateException("unrelated")).get();
              assertThat(stillReported).hasSize(1);
              assertThat(recorder.received).hasSize(1);
            });
  }

  @Test
  void ignoredExceptionsProperty_matchesSubclassOfConfiguredSupertype() {
    runner
        .withPropertyValues(
            "observarium.ignored-exceptions[0]=" + DomainErrorException.class.getName())
        .run(
            context -> {
              Observarium observarium = context.getBean(Observarium.class);

              // DomainErrorSubclassException matches via inheritance even though only its
              // superclass name is configured, and neither type carries @ResponseStatus so this
              // is exercising the property's own matching, not the framework-default predicate.
              List<PostingResult> results =
                  observarium.captureException(new DomainErrorSubclassException()).get();
              assertThat(results).isEmpty();
            });
  }
}
