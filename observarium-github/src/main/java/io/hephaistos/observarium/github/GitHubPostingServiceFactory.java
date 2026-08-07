package io.hephaistos.observarium.github;

import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.posting.PostingServiceDiagnostics;
import io.hephaistos.observarium.posting.PostingServiceFactory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link PostingServiceFactory} implementation for the GitHub posting service.
 *
 * <p>Registered via {@code META-INF/services} for {@link java.util.ServiceLoader} discovery.
 */
public class GitHubPostingServiceFactory implements PostingServiceFactory {

  private static final Logger log = LoggerFactory.getLogger(GitHubPostingServiceFactory.class);
  private static final List<String> REQUIRED_KEYS = List.of("token", "owner", "repo");

  @Override
  public String id() {
    return "github";
  }

  @Override
  public Optional<PostingService> create(Map<String, String> config) {
    String enabled = config.getOrDefault("enabled", "false");
    if (!"true".equalsIgnoreCase(enabled)) {
      PostingServiceDiagnostics.warnIfConfiguredButNotEnabled(log, id(), config, REQUIRED_KEYS);
      return Optional.empty();
    }
    GitHubConfig ghConfig =
        new GitHubConfig(
            config.get("token"),
            config.get("owner"),
            config.get("repo"),
            config.get("label-prefix"),
            config.get("base-url"));
    return Optional.of(new GitHubPostingService(ghConfig));
  }
}
