package io.hephaistos.observarium.jira;

import io.hephaistos.observarium.posting.PostingConfig;
import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.posting.PostingServiceDiagnostics;
import io.hephaistos.observarium.posting.PostingServiceFactory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link PostingServiceFactory} implementation for the Jira posting service.
 *
 * <p>Registered via {@code META-INF/services} for {@link java.util.ServiceLoader} discovery.
 */
public class JiraPostingServiceFactory implements PostingServiceFactory {

  private static final Logger log = LoggerFactory.getLogger(JiraPostingServiceFactory.class);
  private static final List<String> REQUIRED_KEYS =
      List.of("base-url", "username", "api-token", "project-key");

  @Override
  public String id() {
    return "jira";
  }

  @Override
  public Optional<PostingService> create(Map<String, String> config) {
    if (!PostingConfig.booleanValue(config, "enabled", false)) {
      PostingServiceDiagnostics.warnIfConfiguredButNotEnabled(log, id(), config, REQUIRED_KEYS);
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
