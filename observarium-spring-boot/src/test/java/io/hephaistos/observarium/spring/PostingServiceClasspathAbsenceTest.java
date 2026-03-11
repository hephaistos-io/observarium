package io.hephaistos.observarium.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.email.EmailPostingService;
import io.hephaistos.observarium.github.GitHubPostingService;
import io.hephaistos.observarium.gitlab.GitLabPostingService;
import io.hephaistos.observarium.jira.JiraPostingService;
import io.hephaistos.observarium.posting.PostingService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Tests that verify the {@code @ConditionalOnClass} guards on each posting-service
 * auto-configuration: when the posting module's sentinel class is absent from the classpath, the
 * context must still load cleanly and the corresponding {@link PostingService} bean must not be
 * created.
 *
 * <p>Each test uses {@link ApplicationContextRunner#withClassLoader(ClassLoader)} with a {@link
 * FilteredClassLoader} to simulate the module being absent at runtime. The integration modules are
 * on the test classpath, so this is the only way to exercise the absent-classpath branch without a
 * separate Gradle sub-project.
 */
class PostingServiceClasspathAbsenceTest {

  private final ApplicationContextRunner allConfigsRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ObservariumAutoConfiguration.class,
                  GitHubAutoConfiguration.class,
                  JiraAutoConfiguration.class,
                  GitLabAutoConfiguration.class,
                  EmailAutoConfiguration.class));

  // -----------------------------------------------------------------------
  // GitHub module absent
  // -----------------------------------------------------------------------

  @Test
  void contextLoads_whenGitHubClassMissingFromClasspath_despiteGitHubEnabled() {
    allConfigsRunner
        .withClassLoader(new FilteredClassLoader(GitHubPostingService.class))
        .withPropertyValues(
            "observarium.github.enabled=true",
            "observarium.github.token=ghp_test",
            "observarium.github.owner=acme",
            "observarium.github.repo=backend")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(Observarium.class);
              assertThat(context).doesNotHaveBean("gitHubPostingService");
              assertThat(context.getBeansOfType(PostingService.class)).isEmpty();
            });
  }

  // -----------------------------------------------------------------------
  // Jira module absent
  // -----------------------------------------------------------------------

  @Test
  void contextLoads_whenJiraClassMissingFromClasspath_despiteJiraEnabled() {
    allConfigsRunner
        .withClassLoader(new FilteredClassLoader(JiraPostingService.class))
        .withPropertyValues(
            "observarium.jira.enabled=true",
            "observarium.jira.base-url=https://acme.atlassian.net",
            "observarium.jira.username=user@acme.com",
            "observarium.jira.api-token=jira-secret",
            "observarium.jira.project-key=OBS")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(Observarium.class);
              assertThat(context).doesNotHaveBean("jiraPostingService");
              assertThat(context.getBeansOfType(PostingService.class)).isEmpty();
            });
  }

  // -----------------------------------------------------------------------
  // GitLab module absent
  // -----------------------------------------------------------------------

  @Test
  void contextLoads_whenGitLabClassMissingFromClasspath_despiteGitLabEnabled() {
    allConfigsRunner
        .withClassLoader(new FilteredClassLoader(GitLabPostingService.class))
        .withPropertyValues(
            "observarium.gitlab.enabled=true",
            "observarium.gitlab.base-url=https://gitlab.com",
            "observarium.gitlab.private-token=glpat-secret",
            "observarium.gitlab.project-id=42")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(Observarium.class);
              assertThat(context).doesNotHaveBean("gitLabPostingService");
              assertThat(context.getBeansOfType(PostingService.class)).isEmpty();
            });
  }

  // -----------------------------------------------------------------------
  // Email module absent
  // -----------------------------------------------------------------------

  @Test
  void contextLoads_whenEmailClassMissingFromClasspath_despiteEmailEnabled() {
    allConfigsRunner
        .withClassLoader(new FilteredClassLoader(EmailPostingService.class))
        .withPropertyValues(
            "observarium.email.enabled=true",
            "observarium.email.smtp-host=smtp.example.com",
            "observarium.email.from=alerts@example.com",
            "observarium.email.to=team@example.com",
            "observarium.email.username=smtp-user",
            "observarium.email.password=smtp-pass")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(Observarium.class);
              assertThat(context).doesNotHaveBean("emailPostingService");
              assertThat(context.getBeansOfType(PostingService.class)).isEmpty();
            });
  }

  // -----------------------------------------------------------------------
  // All posting modules absent simultaneously
  // -----------------------------------------------------------------------

  @Test
  void contextLoads_whenAllPostingModulesAbsentFromClasspath_withAllServicesEnabled() {
    allConfigsRunner
        .withClassLoader(
            new FilteredClassLoader(
                GitHubPostingService.class,
                JiraPostingService.class,
                GitLabPostingService.class,
                EmailPostingService.class))
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
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(Observarium.class);
              assertThat(context.getBeansOfType(PostingService.class)).isEmpty();
            });
  }
}
