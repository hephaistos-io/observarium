package io.hephaistos.observarium.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.hephaistos.observarium.Observarium;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Integration tests verifying that multiple posting services discovered via the {@link
 * io.hephaistos.observarium.posting.PostingServiceFactory} SPI are correctly wired into the {@link
 * Observarium} bean.
 *
 * <p>All four posting modules are on the test classpath, so their {@code ServiceLoader}
 * registrations are active.
 */
class MultiplePostingServicesAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ObservariumAutoConfiguration.class));

  @Test
  void allFourPostingServicesAreWired_whenAllEnabled() {
    runner
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
              assertThat(observarium.config().postingServiceCount()).isEqualTo(4);
            });
  }

  @Test
  void contextLoads_withNoPostingServicesEnabled() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(Observarium.class);
          Observarium observarium = context.getBean(Observarium.class);
          assertThat(observarium.config().postingServiceCount()).isZero();
        });
  }

  @Test
  void subsetOfServicesWired_whenOnlyGitHubAndJiraEnabled() {
    runner
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
              Observarium observarium = context.getBean(Observarium.class);
              assertThat(observarium.config().postingServiceCount()).isEqualTo(2);
            });
  }
}
