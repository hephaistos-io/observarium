package io.hephaistos.observarium.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.hephaistos.observarium.posting.PostingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Integration tests for the posting-service auto-configurations: {@link GitHubAutoConfiguration},
 * {@link JiraAutoConfiguration}, {@link GitLabAutoConfiguration}, and {@link
 * EmailAutoConfiguration}.
 *
 * <p>Each integration module is on the test classpath so the {@code @ConditionalOnClass} guards are
 * satisfied and the full bean-creation path can be exercised.
 */
class PostingServiceAutoConfigurationTest {

  // -------------------------------------------------------------------------
  // GitHub
  // -------------------------------------------------------------------------

  private final ApplicationContextRunner githubRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(GitHubAutoConfiguration.class));

  @Test
  void githubPostingServiceIsNotCreatedWhenDisabled() {
    githubRunner
        .withPropertyValues("observarium.github.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean("gitHubPostingService"));
  }

  @Test
  void githubPostingServiceIsNotCreatedWhenPropertyAbsent() {
    githubRunner.run(context -> assertThat(context).doesNotHaveBean("gitHubPostingService"));
  }

  @Test
  void githubPostingServiceIsCreatedWhenEnabled() {
    githubRunner
        .withPropertyValues(
            "observarium.github.enabled=true",
            "observarium.github.token=ghp_test",
            "observarium.github.owner=acme",
            "observarium.github.repo=backend")
        .run(
            context -> {
              assertThat(context).hasBean("gitHubPostingService");
              assertThat(context.getBean("gitHubPostingService"))
                  .isInstanceOf(PostingService.class);
            });
  }

  @Test
  void githubLabelPrefixDefaultsToObservarium() {
    githubRunner
        .withPropertyValues(
            "observarium.github.enabled=true",
            "observarium.github.token=ghp_test",
            "observarium.github.owner=acme",
            "observarium.github.repo=backend")
        .run(
            context -> {
              ObservariumProperties props = context.getBean(ObservariumProperties.class);
              assertThat(props.getGithub().getLabelPrefix()).isEqualTo("observarium");
            });
  }

  @Test
  void githubContextFailsWhenEnabledButTokenMissing() {
    githubRunner
        .withPropertyValues(
            "observarium.github.enabled=true",
            "observarium.github.owner=acme",
            "observarium.github.repo=backend")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .isInstanceOf(BeanCreationException.class)
                  .hasRootCauseInstanceOf(IllegalArgumentException.class)
                  .hasRootCauseMessage("GitHub token must not be null or blank");
            });
  }

  @Test
  void githubContextFailsWhenEnabledButOwnerMissing() {
    githubRunner
        .withPropertyValues(
            "observarium.github.enabled=true",
            "observarium.github.token=ghp_test",
            "observarium.github.repo=backend")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .isInstanceOf(BeanCreationException.class)
                  .hasRootCauseInstanceOf(IllegalArgumentException.class)
                  .hasRootCauseMessage("GitHub owner must not be null or blank");
            });
  }

  @Test
  void githubContextFailsWhenEnabledButRepoMissing() {
    githubRunner
        .withPropertyValues(
            "observarium.github.enabled=true",
            "observarium.github.token=ghp_test",
            "observarium.github.owner=acme")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .isInstanceOf(BeanCreationException.class)
                  .hasRootCauseInstanceOf(IllegalArgumentException.class)
                  .hasRootCauseMessage("GitHub repo must not be null or blank");
            });
  }

  @Test
  void githubUserDefinedBeanTakesPrecedence() {
    PostingService customService = mockPostingService("custom-github");
    githubRunner
        .withPropertyValues(
            "observarium.github.enabled=true",
            "observarium.github.token=ghp_test",
            "observarium.github.owner=acme",
            "observarium.github.repo=backend")
        .withBean("gitHubPostingService", PostingService.class, () -> customService)
        .run(
            context -> {
              assertThat(context.getBean("gitHubPostingService")).isSameAs(customService);
            });
  }

  // -------------------------------------------------------------------------
  // Jira
  // -------------------------------------------------------------------------

  private final ApplicationContextRunner jiraRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(JiraAutoConfiguration.class));

  @Test
  void jiraPostingServiceIsNotCreatedWhenDisabled() {
    jiraRunner
        .withPropertyValues("observarium.jira.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean("jiraPostingService"));
  }

  @Test
  void jiraPostingServiceIsNotCreatedWhenPropertyAbsent() {
    jiraRunner.run(context -> assertThat(context).doesNotHaveBean("jiraPostingService"));
  }

  @Test
  void jiraPostingServiceIsCreatedWhenEnabled() {
    jiraRunner
        .withPropertyValues(
            "observarium.jira.enabled=true",
            "observarium.jira.base-url=https://acme.atlassian.net",
            "observarium.jira.username=user@acme.com",
            "observarium.jira.api-token=jira-secret",
            "observarium.jira.project-key=OBS")
        .run(
            context -> {
              assertThat(context).hasBean("jiraPostingService");
              assertThat(context.getBean("jiraPostingService")).isInstanceOf(PostingService.class);
            });
  }

  @Test
  void jiraIssueTypeDefaultsToBug() {
    jiraRunner
        .withPropertyValues(
            "observarium.jira.enabled=true",
            "observarium.jira.base-url=https://acme.atlassian.net",
            "observarium.jira.username=user@acme.com",
            "observarium.jira.api-token=jira-secret",
            "observarium.jira.project-key=OBS")
        .run(
            context -> {
              ObservariumProperties props = context.getBean(ObservariumProperties.class);
              assertThat(props.getJira().getIssueType()).isEqualTo("Bug");
            });
  }

  @Test
  void jiraContextFailsWhenEnabledButBaseUrlMissing() {
    jiraRunner
        .withPropertyValues(
            "observarium.jira.enabled=true",
            "observarium.jira.username=user@acme.com",
            "observarium.jira.api-token=jira-secret",
            "observarium.jira.project-key=OBS")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .isInstanceOf(BeanCreationException.class)
                  .hasRootCauseInstanceOf(IllegalArgumentException.class)
                  .hasRootCauseMessage("JiraConfig.baseUrl must not be blank");
            });
  }

  @Test
  void jiraContextFailsWhenEnabledButUsernameMissing() {
    jiraRunner
        .withPropertyValues(
            "observarium.jira.enabled=true",
            "observarium.jira.base-url=https://acme.atlassian.net",
            "observarium.jira.api-token=jira-secret",
            "observarium.jira.project-key=OBS")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .isInstanceOf(BeanCreationException.class)
                  .hasRootCauseInstanceOf(IllegalArgumentException.class)
                  .hasRootCauseMessage("JiraConfig.username must not be blank");
            });
  }

  @Test
  void jiraContextFailsWhenEnabledButApiTokenMissing() {
    jiraRunner
        .withPropertyValues(
            "observarium.jira.enabled=true",
            "observarium.jira.base-url=https://acme.atlassian.net",
            "observarium.jira.username=user@acme.com",
            "observarium.jira.project-key=OBS")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .isInstanceOf(BeanCreationException.class)
                  .hasRootCauseInstanceOf(IllegalArgumentException.class)
                  .hasRootCauseMessage("JiraConfig.apiToken must not be blank");
            });
  }

  @Test
  void jiraContextFailsWhenEnabledButProjectKeyMissing() {
    jiraRunner
        .withPropertyValues(
            "observarium.jira.enabled=true",
            "observarium.jira.base-url=https://acme.atlassian.net",
            "observarium.jira.username=user@acme.com",
            "observarium.jira.api-token=jira-secret")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .isInstanceOf(BeanCreationException.class)
                  .hasRootCauseInstanceOf(IllegalArgumentException.class)
                  .hasRootCauseMessage("JiraConfig.projectKey must not be blank");
            });
  }

  @Test
  void jiraUserDefinedBeanTakesPrecedence() {
    PostingService customService = mockPostingService("custom-jira");
    jiraRunner
        .withPropertyValues(
            "observarium.jira.enabled=true",
            "observarium.jira.base-url=https://acme.atlassian.net",
            "observarium.jira.username=user@acme.com",
            "observarium.jira.api-token=jira-secret",
            "observarium.jira.project-key=OBS")
        .withBean("jiraPostingService", PostingService.class, () -> customService)
        .run(context -> assertThat(context.getBean("jiraPostingService")).isSameAs(customService));
  }

  // -------------------------------------------------------------------------
  // GitLab
  // -------------------------------------------------------------------------

  private final ApplicationContextRunner gitlabRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(GitLabAutoConfiguration.class));

  @Test
  void gitlabPostingServiceIsNotCreatedWhenDisabled() {
    gitlabRunner
        .withPropertyValues("observarium.gitlab.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean("gitLabPostingService"));
  }

  @Test
  void gitlabPostingServiceIsNotCreatedWhenPropertyAbsent() {
    gitlabRunner.run(context -> assertThat(context).doesNotHaveBean("gitLabPostingService"));
  }

  @Test
  void gitlabPostingServiceIsCreatedWhenEnabled() {
    gitlabRunner
        .withPropertyValues(
            "observarium.gitlab.enabled=true",
            "observarium.gitlab.base-url=https://gitlab.com",
            "observarium.gitlab.private-token=glpat-secret",
            "observarium.gitlab.project-id=42")
        .run(
            context -> {
              assertThat(context).hasBean("gitLabPostingService");
              assertThat(context.getBean("gitLabPostingService"))
                  .isInstanceOf(PostingService.class);
            });
  }

  @Test
  void gitlabContextFailsWhenEnabledButBaseUrlMissing() {
    gitlabRunner
        .withPropertyValues(
            "observarium.gitlab.enabled=true",
            "observarium.gitlab.private-token=glpat-secret",
            "observarium.gitlab.project-id=42")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .isInstanceOf(BeanCreationException.class)
                  .hasRootCauseInstanceOf(IllegalStateException.class)
                  .hasRootCauseMessage(
                      "observarium.gitlab.base-url is required when GitLab posting is enabled");
            });
  }

  @Test
  void gitlabContextFailsWhenEnabledButPrivateTokenMissing() {
    gitlabRunner
        .withPropertyValues(
            "observarium.gitlab.enabled=true",
            "observarium.gitlab.base-url=https://gitlab.com",
            "observarium.gitlab.project-id=42")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .isInstanceOf(BeanCreationException.class)
                  .hasRootCauseInstanceOf(IllegalStateException.class)
                  .hasRootCauseMessage(
                      "observarium.gitlab.private-token is required when GitLab posting is"
                          + " enabled");
            });
  }

  @Test
  void gitlabContextFailsWhenEnabledButProjectIdMissing() {
    gitlabRunner
        .withPropertyValues(
            "observarium.gitlab.enabled=true",
            "observarium.gitlab.base-url=https://gitlab.com",
            "observarium.gitlab.private-token=glpat-secret")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .isInstanceOf(BeanCreationException.class)
                  .hasRootCauseInstanceOf(IllegalStateException.class)
                  .hasRootCauseMessage(
                      "observarium.gitlab.project-id is required when GitLab posting is enabled");
            });
  }

  @Test
  void gitlabUserDefinedBeanTakesPrecedence() {
    PostingService customService = mockPostingService("custom-gitlab");
    gitlabRunner
        .withPropertyValues(
            "observarium.gitlab.enabled=true",
            "observarium.gitlab.base-url=https://gitlab.com",
            "observarium.gitlab.private-token=glpat-secret",
            "observarium.gitlab.project-id=42")
        .withBean("gitLabPostingService", PostingService.class, () -> customService)
        .run(
            context -> assertThat(context.getBean("gitLabPostingService")).isSameAs(customService));
  }

  // -------------------------------------------------------------------------
  // Email
  // -------------------------------------------------------------------------

  private final ApplicationContextRunner emailRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(EmailAutoConfiguration.class));

  @Test
  void emailPostingServiceIsNotCreatedWhenDisabled() {
    emailRunner
        .withPropertyValues("observarium.email.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean("emailPostingService"));
  }

  @Test
  void emailPostingServiceIsNotCreatedWhenPropertyAbsent() {
    emailRunner.run(context -> assertThat(context).doesNotHaveBean("emailPostingService"));
  }

  @Test
  void emailPostingServiceIsCreatedWhenEnabled() {
    emailRunner
        .withPropertyValues(
            "observarium.email.enabled=true",
            "observarium.email.smtp-host=smtp.example.com",
            "observarium.email.smtp-port=587",
            "observarium.email.from=alerts@example.com",
            "observarium.email.to=team@example.com",
            "observarium.email.username=smtp-user",
            "observarium.email.password=smtp-pass")
        .run(
            context -> {
              assertThat(context).hasBean("emailPostingService");
              assertThat(context.getBean("emailPostingService")).isInstanceOf(PostingService.class);
            });
  }

  @Test
  void emailStartTlsDefaultsToTrue() {
    emailRunner
        .withPropertyValues(
            "observarium.email.enabled=true",
            "observarium.email.smtp-host=smtp.example.com",
            "observarium.email.from=alerts@example.com",
            "observarium.email.to=team@example.com",
            "observarium.email.username=smtp-user",
            "observarium.email.password=smtp-pass")
        .run(
            context -> {
              ObservariumProperties props = context.getBean(ObservariumProperties.class);
              assertThat(props.getEmail().isStartTls()).isTrue();
            });
  }

  @Test
  void emailContextFailsWhenEnabledButSmtpHostMissing() {
    emailRunner
        .withPropertyValues(
            "observarium.email.enabled=true",
            "observarium.email.from=alerts@example.com",
            "observarium.email.to=team@example.com")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .isInstanceOf(BeanCreationException.class)
                  .hasRootCauseInstanceOf(IllegalStateException.class)
                  .hasRootCauseMessage(
                      "observarium.email.smtp-host is required when email posting is enabled");
            });
  }

  @Test
  void emailContextFailsWhenEnabledButFromMissing() {
    emailRunner
        .withPropertyValues(
            "observarium.email.enabled=true",
            "observarium.email.smtp-host=smtp.example.com",
            "observarium.email.to=team@example.com")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .isInstanceOf(BeanCreationException.class)
                  .hasRootCauseInstanceOf(IllegalStateException.class)
                  .hasRootCauseMessage(
                      "observarium.email.from is required when email posting is enabled");
            });
  }

  @Test
  void emailContextFailsWhenEnabledButToMissing() {
    emailRunner
        .withPropertyValues(
            "observarium.email.enabled=true",
            "observarium.email.smtp-host=smtp.example.com",
            "observarium.email.from=alerts@example.com")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .isInstanceOf(BeanCreationException.class)
                  .hasRootCauseInstanceOf(IllegalStateException.class)
                  .hasRootCauseMessage(
                      "observarium.email.to is required when email posting is enabled");
            });
  }

  @Test
  void emailUserDefinedBeanTakesPrecedence() {
    PostingService customService = mockPostingService("custom-email");
    emailRunner
        .withPropertyValues(
            "observarium.email.enabled=true",
            "observarium.email.smtp-host=smtp.example.com",
            "observarium.email.from=alerts@example.com",
            "observarium.email.to=team@example.com",
            "observarium.email.username=smtp-user",
            "observarium.email.password=smtp-pass")
        .withBean("emailPostingService", PostingService.class, () -> customService)
        .run(context -> assertThat(context.getBean("emailPostingService")).isSameAs(customService));
  }

  // -------------------------------------------------------------------------
  // Helper
  // -------------------------------------------------------------------------

  /**
   * Returns a minimal {@link PostingService} stub with the given name. This avoids a Mockito
   * dependency in context runner lambdas, which can cause serialisation issues in some Spring Boot
   * test infrastructure.
   */
  private PostingService mockPostingService(String name) {
    return new PostingService() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public io.hephaistos.observarium.posting.DuplicateSearchResult findDuplicate(
          io.hephaistos.observarium.event.ExceptionEvent event) {
        return io.hephaistos.observarium.posting.DuplicateSearchResult.notFound();
      }

      @Override
      public io.hephaistos.observarium.posting.PostingResult createIssue(
          io.hephaistos.observarium.event.ExceptionEvent event) {
        return io.hephaistos.observarium.posting.PostingResult.failure("stub");
      }

      @Override
      public io.hephaistos.observarium.posting.PostingResult commentOnIssue(
          String externalIssueId, io.hephaistos.observarium.event.ExceptionEvent event) {
        return io.hephaistos.observarium.posting.PostingResult.failure("stub");
      }
    };
  }
}
