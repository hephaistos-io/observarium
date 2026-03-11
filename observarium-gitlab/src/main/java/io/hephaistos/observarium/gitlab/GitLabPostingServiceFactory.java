package io.hephaistos.observarium.gitlab;

import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.posting.PostingServiceFactory;
import java.util.Map;
import java.util.Optional;

/**
 * {@link PostingServiceFactory} implementation for the GitLab posting service.
 *
 * <p>Registered via {@code META-INF/services} for {@link java.util.ServiceLoader} discovery.
 */
public class GitLabPostingServiceFactory implements PostingServiceFactory {

  @Override
  public String id() {
    return "gitlab";
  }

  @Override
  public Optional<PostingService> create(Map<String, String> config) {
    String enabled = config.getOrDefault("enabled", "false");
    if (!"true".equalsIgnoreCase(enabled)) {
      return Optional.empty();
    }
    GitLabConfig glConfig =
        new GitLabConfig(
            config.get("base-url"), config.get("private-token"), config.get("project-id"));
    return Optional.of(new GitLabPostingService(glConfig));
  }
}
