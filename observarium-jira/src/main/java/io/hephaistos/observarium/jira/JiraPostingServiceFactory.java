package io.hephaistos.observarium.jira;

import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.posting.PostingServiceFactory;
import java.util.Map;
import java.util.Optional;

/**
 * {@link PostingServiceFactory} implementation for the Jira posting service.
 *
 * <p>Registered via {@code META-INF/services} for {@link java.util.ServiceLoader} discovery.
 */
public class JiraPostingServiceFactory implements PostingServiceFactory {

  @Override
  public String id() {
    return "jira";
  }

  @Override
  public Optional<PostingService> create(Map<String, String> config) {
    String enabled = config.getOrDefault("enabled", "false");
    if (!"true".equalsIgnoreCase(enabled)) {
      return Optional.empty();
    }
    JiraConfig jiraConfig =
        new JiraConfig(
            config.get("base-url"),
            config.get("username"),
            config.get("api-token"),
            config.get("project-key"),
            config.get("issue-type"));
    return Optional.of(new JiraPostingService(jiraConfig));
  }
}
