package io.hephaistos.observarium.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Tests that verify that a user-defined {@link PostingService} bean coexists with SPI-discovered
 * posting services.
 *
 * <p>When a user registers their own {@link PostingService} bean, it is wired into the {@link
 * Observarium} instance alongside any services discovered via the {@link
 * io.hephaistos.observarium.posting.PostingServiceFactory} SPI.
 */
class CustomBeanCoexistenceTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ObservariumAutoConfiguration.class));

  @Test
  void customPostingServiceAndSpiService_bothRegisteredInObservarium() {
    PostingService customService = stubPostingService("custom-service");

    runner
        .withBean("customPostingService", PostingService.class, () -> customService)
        .withPropertyValues(
            "observarium.github.enabled=true",
            "observarium.github.token=ghp_test",
            "observarium.github.owner=acme",
            "observarium.github.repo=backend")
        .run(
            context -> {
              assertThat(context).hasSingleBean(Observarium.class);
              Observarium observarium = context.getBean(Observarium.class);
              // 1 custom bean + 1 SPI-discovered GitHub service = 2
              assertThat(observarium.config().postingServiceCount()).isEqualTo(2);
            });
  }

  @Test
  void customPostingServiceAndAllFourSpiServices_fiveServicesRegistered() {
    PostingService customService = stubPostingService("my-custom-service");

    runner
        .withBean("myCustomPostingService", PostingService.class, () -> customService)
        .withPropertyValues(
            "observarium.github.enabled=true",
            "observarium.github.token=ghp_test",
            "observarium.github.owner=acme",
            "observarium.github.repo=backend",
            "observarium.jira.enabled=true",
            "observarium.jira.base-url=https://acme.atlassian.net",
            "observarium.jira.username=user@acme.com",
            "observarium.jira.api-token=jira-secret",
            "observarium.jira.project-key=OBS",
            "observarium.gitlab.enabled=true",
            "observarium.gitlab.base-url=https://gitlab.com",
            "observarium.gitlab.private-token=glpat-secret",
            "observarium.gitlab.project-id=42",
            "observarium.email.enabled=true",
            "observarium.email.smtp-host=smtp.example.com",
            "observarium.email.from=alerts@example.com",
            "observarium.email.to=team@example.com",
            "observarium.email.username=smtp-user",
            "observarium.email.password=smtp-pass")
        .run(
            context -> {
              assertThat(context).hasSingleBean(Observarium.class);
              Observarium observarium = context.getBean(Observarium.class);
              assertThat(observarium.config().postingServiceCount()).isEqualTo(5);
            });
  }

  private PostingService stubPostingService(String name) {
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
        return PostingResult.failure("stub");
      }

      @Override
      public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
        return PostingResult.failure("stub");
      }
    };
  }
}
