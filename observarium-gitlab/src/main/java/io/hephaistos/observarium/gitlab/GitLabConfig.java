package io.hephaistos.observarium.gitlab;

/**
 * Configuration for the GitLab posting service.
 *
 * @param baseUrl The GitLab instance base URL (e.g. "https://gitlab.com")
 * @param privateToken A Personal Access Token (PAT) with at least api scope
 * @param projectId The numeric project ID or URL-encoded namespace/path
 */
public record GitLabConfig(String baseUrl, String privateToken, String projectId) {

  /** Compact canonical constructor that validates required fields. */
  public GitLabConfig {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("GitLabConfig.baseUrl must not be blank");
    }
    if (privateToken == null || privateToken.isBlank()) {
      throw new IllegalArgumentException("GitLabConfig.privateToken must not be blank");
    }
    if (projectId == null || projectId.isBlank()) {
      throw new IllegalArgumentException("GitLabConfig.projectId must not be blank");
    }
    baseUrl = baseUrl.stripTrailing().replaceAll("/+$", "");
  }
}
