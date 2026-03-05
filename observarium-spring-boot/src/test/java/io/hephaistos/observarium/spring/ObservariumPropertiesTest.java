package io.hephaistos.observarium.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.hephaistos.observarium.scrub.ScrubLevel;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ObservariumProperties} and its nested configuration classes.
 *
 * <p>Exercises every getter/setter to ensure JaCoCo instruction coverage counts them. These are
 * plain POJO tests — no Spring context is required.
 */
class ObservariumPropertiesTest {

  // -------------------------------------------------------------------------
  // Top-level properties
  // -------------------------------------------------------------------------

  @Test
  void defaultValuesAreCorrect() {
    ObservariumProperties props = new ObservariumProperties();

    assertThat(props.isEnabled()).isTrue();
    assertThat(props.getScrubLevel()).isEqualTo(ScrubLevel.BASIC);
    assertThat(props.getTraceIdMdcKey()).isEqualTo("trace_id");
    assertThat(props.getSpanIdMdcKey()).isEqualTo("span_id");
  }

  @Test
  void topLevelSettersRoundTrip() {
    ObservariumProperties props = new ObservariumProperties();

    props.setEnabled(false);
    assertThat(props.isEnabled()).isFalse();

    props.setScrubLevel(ScrubLevel.STRICT);
    assertThat(props.getScrubLevel()).isEqualTo(ScrubLevel.STRICT);

    props.setTraceIdMdcKey("X-Trace-Id");
    assertThat(props.getTraceIdMdcKey()).isEqualTo("X-Trace-Id");

    props.setSpanIdMdcKey("X-Span-Id");
    assertThat(props.getSpanIdMdcKey()).isEqualTo("X-Span-Id");
  }

  @Test
  void nestedObjectGettersReturnNonNull() {
    ObservariumProperties props = new ObservariumProperties();

    assertThat(props.getGithub()).isNotNull();
    assertThat(props.getJira()).isNotNull();
    assertThat(props.getGitlab()).isNotNull();
    assertThat(props.getEmail()).isNotNull();
  }

  @Test
  void nestedObjectSettersRoundTrip() {
    ObservariumProperties props = new ObservariumProperties();

    ObservariumProperties.GitHub github = new ObservariumProperties.GitHub();
    props.setGithub(github);
    assertThat(props.getGithub()).isSameAs(github);

    ObservariumProperties.Jira jira = new ObservariumProperties.Jira();
    props.setJira(jira);
    assertThat(props.getJira()).isSameAs(jira);

    ObservariumProperties.GitLab gitlab = new ObservariumProperties.GitLab();
    props.setGitlab(gitlab);
    assertThat(props.getGitlab()).isSameAs(gitlab);

    ObservariumProperties.Email email = new ObservariumProperties.Email();
    props.setEmail(email);
    assertThat(props.getEmail()).isSameAs(email);
  }

  // -------------------------------------------------------------------------
  // GitHub nested class
  // -------------------------------------------------------------------------

  @Test
  void githubDefaultValues() {
    ObservariumProperties.GitHub github = new ObservariumProperties.GitHub();

    assertThat(github.isEnabled()).isFalse();
    assertThat(github.getToken()).isNull();
    assertThat(github.getOwner()).isNull();
    assertThat(github.getRepo()).isNull();
    assertThat(github.getLabelPrefix()).isEqualTo("observarium");
  }

  @Test
  void githubSettersRoundTrip() {
    ObservariumProperties.GitHub github = new ObservariumProperties.GitHub();

    github.setEnabled(true);
    assertThat(github.isEnabled()).isTrue();

    github.setToken("ghp_secret");
    assertThat(github.getToken()).isEqualTo("ghp_secret");

    github.setOwner("acme");
    assertThat(github.getOwner()).isEqualTo("acme");

    github.setRepo("backend");
    assertThat(github.getRepo()).isEqualTo("backend");

    github.setLabelPrefix("bug-tracker");
    assertThat(github.getLabelPrefix()).isEqualTo("bug-tracker");
  }

  // -------------------------------------------------------------------------
  // Jira nested class
  // -------------------------------------------------------------------------

  @Test
  void jiraDefaultValues() {
    ObservariumProperties.Jira jira = new ObservariumProperties.Jira();

    assertThat(jira.isEnabled()).isFalse();
    assertThat(jira.getBaseUrl()).isNull();
    assertThat(jira.getUsername()).isNull();
    assertThat(jira.getApiToken()).isNull();
    assertThat(jira.getProjectKey()).isNull();
    assertThat(jira.getIssueType()).isEqualTo("Bug");
  }

  @Test
  void jiraSettersRoundTrip() {
    ObservariumProperties.Jira jira = new ObservariumProperties.Jira();

    jira.setEnabled(true);
    assertThat(jira.isEnabled()).isTrue();

    jira.setBaseUrl("https://acme.atlassian.net");
    assertThat(jira.getBaseUrl()).isEqualTo("https://acme.atlassian.net");

    jira.setUsername("user@acme.com");
    assertThat(jira.getUsername()).isEqualTo("user@acme.com");

    jira.setApiToken("jira-secret");
    assertThat(jira.getApiToken()).isEqualTo("jira-secret");

    jira.setProjectKey("OBS");
    assertThat(jira.getProjectKey()).isEqualTo("OBS");

    jira.setIssueType("Incident");
    assertThat(jira.getIssueType()).isEqualTo("Incident");
  }

  // -------------------------------------------------------------------------
  // GitLab nested class
  // -------------------------------------------------------------------------

  @Test
  void gitlabDefaultValues() {
    ObservariumProperties.GitLab gitlab = new ObservariumProperties.GitLab();

    assertThat(gitlab.isEnabled()).isFalse();
    assertThat(gitlab.getBaseUrl()).isNull();
    assertThat(gitlab.getPrivateToken()).isNull();
    assertThat(gitlab.getProjectId()).isNull();
  }

  @Test
  void gitlabSettersRoundTrip() {
    ObservariumProperties.GitLab gitlab = new ObservariumProperties.GitLab();

    gitlab.setEnabled(true);
    assertThat(gitlab.isEnabled()).isTrue();

    gitlab.setBaseUrl("https://gitlab.com");
    assertThat(gitlab.getBaseUrl()).isEqualTo("https://gitlab.com");

    gitlab.setPrivateToken("glpat-secret");
    assertThat(gitlab.getPrivateToken()).isEqualTo("glpat-secret");

    gitlab.setProjectId("12345");
    assertThat(gitlab.getProjectId()).isEqualTo("12345");
  }

  // -------------------------------------------------------------------------
  // Email nested class
  // -------------------------------------------------------------------------

  @Test
  void emailDefaultValues() {
    ObservariumProperties.Email email = new ObservariumProperties.Email();

    assertThat(email.isEnabled()).isFalse();
    assertThat(email.getSmtpHost()).isNull();
    assertThat(email.getSmtpPort()).isEqualTo(587);
    assertThat(email.getFrom()).isNull();
    assertThat(email.getTo()).isNull();
    assertThat(email.getUsername()).isNull();
    assertThat(email.getPassword()).isNull();
    assertThat(email.isStartTls()).isTrue();
  }

  @Test
  void emailSettersRoundTrip() {
    ObservariumProperties.Email email = new ObservariumProperties.Email();

    email.setEnabled(true);
    assertThat(email.isEnabled()).isTrue();

    email.setSmtpHost("smtp.example.com");
    assertThat(email.getSmtpHost()).isEqualTo("smtp.example.com");

    email.setSmtpPort(465);
    assertThat(email.getSmtpPort()).isEqualTo(465);

    email.setFrom("alerts@example.com");
    assertThat(email.getFrom()).isEqualTo("alerts@example.com");

    email.setTo("team@example.com");
    assertThat(email.getTo()).isEqualTo("team@example.com");

    email.setUsername("smtp-user");
    assertThat(email.getUsername()).isEqualTo("smtp-user");

    email.setPassword("smtp-pass");
    assertThat(email.getPassword()).isEqualTo("smtp-pass");

    email.setStartTls(false);
    assertThat(email.isStartTls()).isFalse();
  }
}
