package io.hephaistos.observarium.spring;

import io.hephaistos.observarium.scrub.ScrubLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Observarium, bound from the {@code observarium.*} namespace.
 *
 * <p>All nested config classes use getters/setters (not records) so that Spring's {@link
 * ConfigurationProperties} binding mechanism can populate them via reflection.
 */
@ConfigurationProperties(prefix = "observarium")
public class ObservariumProperties {

  private boolean enabled = true;
  private ScrubLevel scrubLevel = ScrubLevel.BASIC;
  private String traceIdMdcKey = "trace_id";
  private String spanIdMdcKey = "span_id";

  private GitHub github = new GitHub();
  private Jira jira = new Jira();
  private GitLab gitlab = new GitLab();
  private Email email = new Email();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public ScrubLevel getScrubLevel() {
    return scrubLevel;
  }

  public void setScrubLevel(ScrubLevel scrubLevel) {
    this.scrubLevel = scrubLevel;
  }

  public String getTraceIdMdcKey() {
    return traceIdMdcKey;
  }

  public void setTraceIdMdcKey(String traceIdMdcKey) {
    this.traceIdMdcKey = traceIdMdcKey;
  }

  public String getSpanIdMdcKey() {
    return spanIdMdcKey;
  }

  public void setSpanIdMdcKey(String spanIdMdcKey) {
    this.spanIdMdcKey = spanIdMdcKey;
  }

  public GitHub getGithub() {
    return github;
  }

  public void setGithub(GitHub github) {
    this.github = github;
  }

  public Jira getJira() {
    return jira;
  }

  public void setJira(Jira jira) {
    this.jira = jira;
  }

  public GitLab getGitlab() {
    return gitlab;
  }

  public void setGitlab(GitLab gitlab) {
    this.gitlab = gitlab;
  }

  public Email getEmail() {
    return email;
  }

  public void setEmail(Email email) {
    this.email = email;
  }

  // -------------------------------------------------------------------------
  // Nested configuration classes
  // -------------------------------------------------------------------------

  public static class GitHub {
    private boolean enabled = false;
    private String token;
    private String owner;
    private String repo;
    private String labelPrefix = "observarium";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getToken() {
      return token;
    }

    public void setToken(String token) {
      this.token = token;
    }

    public String getOwner() {
      return owner;
    }

    public void setOwner(String owner) {
      this.owner = owner;
    }

    public String getRepo() {
      return repo;
    }

    public void setRepo(String repo) {
      this.repo = repo;
    }

    public String getLabelPrefix() {
      return labelPrefix;
    }

    public void setLabelPrefix(String labelPrefix) {
      this.labelPrefix = labelPrefix;
    }
  }

  public static class Jira {
    private boolean enabled = false;
    private String baseUrl;
    private String username;
    private String apiToken;
    private String projectKey;
    private String issueType = "Bug";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getApiToken() {
      return apiToken;
    }

    public void setApiToken(String apiToken) {
      this.apiToken = apiToken;
    }

    public String getProjectKey() {
      return projectKey;
    }

    public void setProjectKey(String projectKey) {
      this.projectKey = projectKey;
    }

    public String getIssueType() {
      return issueType;
    }

    public void setIssueType(String issueType) {
      this.issueType = issueType;
    }
  }

  public static class GitLab {
    private boolean enabled = false;
    private String baseUrl;
    private String privateToken;
    private String projectId;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getPrivateToken() {
      return privateToken;
    }

    public void setPrivateToken(String privateToken) {
      this.privateToken = privateToken;
    }

    public String getProjectId() {
      return projectId;
    }

    public void setProjectId(String projectId) {
      this.projectId = projectId;
    }
  }

  public static class Email {
    private boolean enabled = false;
    private String smtpHost;
    private int smtpPort = 587;
    private String from;
    private String to;
    private String username;
    private String password;
    private boolean startTls = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getSmtpHost() {
      return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
      this.smtpHost = smtpHost;
    }

    public int getSmtpPort() {
      return smtpPort;
    }

    public void setSmtpPort(int smtpPort) {
      this.smtpPort = smtpPort;
    }

    public String getFrom() {
      return from;
    }

    public void setFrom(String from) {
      this.from = from;
    }

    public String getTo() {
      return to;
    }

    public void setTo(String to) {
      this.to = to;
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      return password;
    }

    public void setPassword(String password) {
      this.password = password;
    }

    public boolean isStartTls() {
      return startTls;
    }

    public void setStartTls(boolean startTls) {
      this.startTls = startTls;
    }
  }
}
