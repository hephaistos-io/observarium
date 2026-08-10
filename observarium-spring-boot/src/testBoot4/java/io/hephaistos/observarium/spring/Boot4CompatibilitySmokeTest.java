package io.hephaistos.observarium.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.fingerprint.ExceptionFingerprinter;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.scrub.DataScrubber;
import io.hephaistos.observarium.trace.TraceContextProvider;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Cross-build smoke test for issue #22: boots {@link ObservariumAutoConfiguration} against Spring
 * Boot 4 (resolved from the Boot 4 BOM in the {@code testBoot4} source set — see {@code
 * observarium-spring-boot/build.gradle} for why a second source set was chosen over a CI matrix
 * axis) and verifies the module still works: the expected beans register and a captured exception
 * reaches a stub {@link PostingService}.
 *
 * <p>{@code observarium-spring-boot} itself still compiles against Spring Boot 3.4.3 (see the main
 * {@code test} source set for the full behavioural test suite); this class only proves that the
 * compiled module also resolves and functions correctly when a consuming application has upgraded
 * to Boot 4, as described in issue #22.
 */
class Boot4CompatibilitySmokeTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ObservariumAutoConfiguration.class));

  @Test
  void expectedBeansRegisterOnSpringBoot4() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(Observarium.class);
          assertThat(context).hasSingleBean(ExceptionFingerprinter.class);
          assertThat(context).hasSingleBean(DataScrubber.class);
          assertThat(context).hasSingleBean(TraceContextProvider.class);
          assertThat(context).hasSingleBean(ObservariumGlobalExceptionHandler.class);
        });
  }

  @Test
  void capturedExceptionReachesStubPostingServiceOnSpringBoot4() throws InterruptedException {
    List<ExceptionEvent> captured = new CopyOnWriteArrayList<>();

    runner
        .withBean(
            "stubPostingService", PostingService.class, () -> recordingPostingService(captured))
        .run(
            context -> {
              Observarium observarium = context.getBean(Observarium.class);
              observarium.captureException(new RuntimeException("boot4-smoke"));

              long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10);
              while (captured.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
              }

              assertThat(captured).hasSize(1);
              assertThat(captured.get(0).message()).isEqualTo("boot4-smoke");
            });
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
}
