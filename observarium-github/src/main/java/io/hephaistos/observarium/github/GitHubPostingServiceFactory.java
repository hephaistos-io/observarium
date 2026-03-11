package io.hephaistos.observarium.github;

import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.posting.PostingServiceFactory;
import java.util.Map;
import java.util.Optional;

/**
 * {@link PostingServiceFactory} implementation for the GitHub posting service.
 *
 * <p>Registered via {@code META-INF/services} for {@link java.util.ServiceLoader} discovery.
 */
public class GitHubPostingServiceFactory implements PostingServiceFactory {

  @Override
  public String id() {
    return "github";
  }

  @Override
  public Optional<PostingService> create(Map<String, String> config) {
    String enabled = config.getOrDefault("enabled", "false");
    if (!"true".equalsIgnoreCase(enabled)) {
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
