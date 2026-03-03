package io.hephaistos.observarium.gitlab;

/**
 * Configuration for the GitLab posting service.
 *
 * @param baseUrl      The GitLab instance base URL (e.g. "https://gitlab.com")
 * @param privateToken A Personal Access Token (PAT) with at least api scope
 * @param projectId    The numeric project ID or URL-encoded namespace/path
 */
public record GitLabConfig(
    String baseUrl,
    String privateToken,
    String projectId
) {}
