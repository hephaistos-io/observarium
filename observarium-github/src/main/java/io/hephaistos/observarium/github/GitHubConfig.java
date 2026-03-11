package io.hephaistos.observarium.github;

/**
 * Configuration for the GitHub PostingService.
 *
 * @param token GitHub Personal Access Token or app installation token. Must have {@code repo} scope
 *     (or {@code public_repo} for public repos).
 * @param owner GitHub repository owner — an organization name or a user login.
 * @param repo GitHub repository name.
 * @param labelPrefix Label applied to all issues created by Observarium. Defaults to {@code
 *     "observarium"} when {@code null} is supplied.
 * @param baseUrl GitHub API base URL. Defaults to {@code "https://api.github.com"} when {@code
 *     null} or blank is supplied. Override for GitHub Enterprise or testing.
 */
public record GitHubConfig(
    String token, String owner, String repo, String labelPrefix, String baseUrl) {

  private static final String DEFAULT_API_BASE = "https://api.github.com";

  /**
   * Canonical constructor — applies defaults for {@code baseUrl} and {@code labelPrefix} when the
   * caller passes {@code null}.
   */
  public GitHubConfig {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("GitHub token must not be null or blank");
    }
    if (owner == null || owner.isBlank()) {
      throw new IllegalArgumentException("GitHub owner must not be null or blank");
    }
    if (repo == null || repo.isBlank()) {
      throw new IllegalArgumentException("GitHub repo must not be null or blank");
    }
    if (labelPrefix == null || labelPrefix.isBlank()) {
      labelPrefix = "observarium";
    }
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = DEFAULT_API_BASE;
    }
    baseUrl = baseUrl.stripTrailing().replaceAll("/+$", "");
  }

  /** Convenience factory — uses the default API base URL and {@code "observarium"} label prefix. */
  public static GitHubConfig of(String token, String owner, String repo) {
    return new GitHubConfig(token, owner, repo, "observarium", null);
  }
}
