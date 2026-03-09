package io.hephaistos.observarium.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.posting.PostingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Integration tests for the composite scenario where multiple posting-service auto-configurations
 * are loaded together alongside {@link ObservariumAutoConfiguration}.
 *
 * <p>Verifies that the correct number of {@link PostingService} beans are registered when all,
 * some, or none of the integration modules are enabled, and that the {@link Observarium} bean is
 * always present regardless of which services are active.
 *
 * <p>All four integration modules ({@code observarium-github}, {@code observarium-jira}, {@code
 * observarium-gitlab}, {@code observarium-email}) are on the test classpath, so the
 * {@code @ConditionalOnClass} guards on each auto-configuration are satisfied.
 */
class MultiplePostingServicesAutoConfigurationTest {

  private final ApplicationContextRunner allConfigsRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ObservariumAutoConfiguration.class,
                  GitHubAutoConfiguration.class,
                  JiraAutoConfiguration.class,
                  GitLabAutoConfiguration.class,
                  EmailAutoConfiguration.class));

  @Test
  void allFourPostingServicesAreWired_whenAllEnabled() {
    allConfigsRunner
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
              assertThat(context.getBeansOfType(PostingService.class)).hasSize(4);
            });
  }

  @Test
  void contextLoads_withNoPostingServicesEnabled() {
    allConfigsRunner.run(
        context -> {
          assertThat(context).hasSingleBean(Observarium.class);
          assertThat(context.getBeansOfType(PostingService.class)).isEmpty();
        });
  }

  @Test
  void subsetOfServicesWired_whenOnlyGitHubAndJiraEnabled() {
    allConfigsRunner
        .withPropertyValues(
            "observarium.github.enabled=true",
            "observarium.github.token=ghp_test",
            "observarium.github.owner=acme",
            "observarium.github.repo=backend",
            "observarium.jira.enabled=true",
            "observarium.jira.base-url=https://acme.atlassian.net",
            "observarium.jira.username=user@acme.com",
            "observarium.jira.api-token=jira-secret",
            "observarium.jira.project-key=OBS")
        .run(
            context -> {
              assertThat(context).hasSingleBean(Observarium.class);
              assertThat(context.getBeansOfType(PostingService.class)).hasSize(2);
              assertThat(context).hasBean("gitHubPostingService");
              assertThat(context).hasBean("jiraPostingService");
              assertThat(context).doesNotHaveBean("gitLabPostingService");
              assertThat(context).doesNotHaveBean("emailPostingService");
            });
  }
}
