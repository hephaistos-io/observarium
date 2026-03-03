package io.hephaistos.observarium.jira;

/**
 * Configuration record for the Jira posting service.
 *
 * <p>{@code baseUrl} is the root URL of the Jira instance, e.g. {@code https://mycompany.atlassian.net}.
 * {@code username} is the email address used to authenticate with Jira Cloud.
 * {@code apiToken} is the personal API token that pairs with {@code username}.
 * {@code projectKey} is the Jira project key, e.g. {@code OBS}.
 * {@code issueType} is the name of the issue type to create, defaults to {@code Bug}.
 */
public record JiraConfig(
    String baseUrl,
    String username,
    String apiToken,
    String projectKey,
    String issueType
) {

    /**
     * Compact canonical constructor that validates required fields and normalises the base URL.
     */
    public JiraConfig {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("JiraConfig.baseUrl must not be blank");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("JiraConfig.username must not be blank");
        }
        if (apiToken == null || apiToken.isBlank()) {
            throw new IllegalArgumentException("JiraConfig.apiToken must not be blank");
        }
        if (projectKey == null || projectKey.isBlank()) {
            throw new IllegalArgumentException("JiraConfig.projectKey must not be blank");
        }
        // Trim trailing slash so callers can concatenate paths without double-slash.
        baseUrl = baseUrl.stripTrailing().replaceAll("/+$", "");
        issueType = (issueType == null || issueType.isBlank()) ? "Bug" : issueType;
    }

    /**
     * Convenience factory that uses {@code "Bug"} as the default issue type.
     */
    public static JiraConfig of(String baseUrl, String username, String apiToken, String projectKey) {
        return new JiraConfig(baseUrl, username, apiToken, projectKey, "Bug");
    }
}
