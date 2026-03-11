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
 * Tests that verify that a user-defined {@link PostingService} bean coexists with config-driven
 * posting service beans produced by the auto-configurations.
 *
 * <p>When a user registers their own {@link PostingService} bean with a name that differs from the
 * auto-configured bean names ({@code gitHubPostingService}, {@code jiraPostingService}, etc.), both
 * the custom bean and the auto-configured bean must be present in the context and both must be
 * wired into the {@link Observarium} instance.
 */
class CustomBeanCoexistenceTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ObservariumAutoConfiguration.class,
                  GitHubAutoConfiguration.class,
                  JiraAutoConfiguration.class,
                  GitLabAutoConfiguration.class,
                  EmailAutoConfiguration.class));

  // -----------------------------------------------------------------------
  // Custom bean + one config-driven service
  // -----------------------------------------------------------------------

  @Test
  void customPostingServiceAndGitHubService_bothRegisteredInObservarium() {
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

              // Both the custom and auto-configured beans must be present.
              assertThat(context).hasBean("customPostingService");
              assertThat(context).hasBean("gitHubPostingService");
              assertThat(context.getBeansOfType(PostingService.class)).hasSize(2);

              // Observarium must have picked up both services.
              Observarium observarium = context.getBean(Observarium.class);
              assertThat(observarium.config().postingServiceCount()).isEqualTo(2);
            });
  }

  // -----------------------------------------------------------------------
  // Custom bean + all four config-driven services
  // -----------------------------------------------------------------------

  @Test
  void customPostingServiceAndAllFourConfigServices_fiveBeansRegisteredInObservarium() {
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
              assertThat(context.getBeansOfType(PostingService.class)).hasSize(5);

              Observarium observarium = context.getBean(Observarium.class);
              assertThat(observarium.config().postingServiceCount()).isEqualTo(5);
            });
  }

  // -----------------------------------------------------------------------
  // Custom bean does not suppress config-driven service with a different name
  // -----------------------------------------------------------------------

  /**
   * Confirms that a user's custom bean does not accidentally suppress a config-driven bean when the
   * bean names differ. The {@code @ConditionalOnMissingBean(name = "gitHubPostingService")} guard
   * on {@link GitHubAutoConfiguration} only fires when a bean named exactly {@code
   * gitHubPostingService} is already present.
   */
  @Test
  void customBeanWithDifferentName_doesNotSuppressAutoConfiguredGitHubBean() {
    PostingService customService = stubPostingService("my-own-tracker");

    runner
        .withBean("myOwnTracker", PostingService.class, () -> customService)
        .withPropertyValues(
            "observarium.github.enabled=true",
            "observarium.github.token=ghp_test",
            "observarium.github.owner=acme",
            "observarium.github.repo=backend")
        .run(
            context -> {
              assertThat(context).hasBean("myOwnTracker");
              assertThat(context).hasBean("gitHubPostingService");
              assertThat(context.getBeansOfType(PostingService.class)).hasSize(2);
            });
  }

  /**
   * Confirms that a user's custom bean registered with the reserved name {@code
   * gitHubPostingService} does suppress the auto-configured GitHub bean (existing
   * {@code @ConditionalOnMissingBean} behaviour), even when GitHub is enabled in properties.
   */
  @Test
  void customBeanWithReservedName_suppressesAutoConfiguredGitHubBean() {
    PostingService customService = stubPostingService("my-github-override");

    runner
        .withBean("gitHubPostingService", PostingService.class, () -> customService)
        .withPropertyValues(
            "observarium.github.enabled=true",
            "observarium.github.token=ghp_test",
            "observarium.github.owner=acme",
            "observarium.github.repo=backend")
        .run(
            context -> {
              assertThat(context.getBeansOfType(PostingService.class)).hasSize(1);
              assertThat(context.getBean("gitHubPostingService")).isSameAs(customService);

              // Observarium still has one service — the custom override.
              Observarium observarium = context.getBean(Observarium.class);
              assertThat(observarium.config().postingServiceCount()).isEqualTo(1);
            });
  }

  // -----------------------------------------------------------------------
  // Helper
  // -----------------------------------------------------------------------

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
