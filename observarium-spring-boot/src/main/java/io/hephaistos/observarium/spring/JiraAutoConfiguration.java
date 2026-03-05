package io.hephaistos.observarium.spring;

import io.hephaistos.observarium.jira.JiraConfig;
import io.hephaistos.observarium.jira.JiraPostingService;
import io.hephaistos.observarium.posting.PostingService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the Jira {@link PostingService}.
 *
 * <p>Activated only when:
 *
 * <ol>
 *   <li>The {@code observarium-jira} module is on the runtime classpath ({@code JiraPostingService}
 *       class is present), and
 *   <li>{@code observarium.jira.enabled=true} is set in application properties.
 * </ol>
 */
@AutoConfiguration
@ConditionalOnClass(name = "io.hephaistos.observarium.jira.JiraPostingService")
@ConditionalOnProperty(name = "observarium.jira.enabled", havingValue = "true")
@EnableConfigurationProperties(ObservariumProperties.class)
public class JiraAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "jiraPostingService")
  public PostingService jiraPostingService(ObservariumProperties properties) {
    ObservariumProperties.Jira jira = properties.getJira();
    JiraConfig config =
        new JiraConfig(
            jira.getBaseUrl(),
            jira.getUsername(),
            jira.getApiToken(),
            jira.getProjectKey(),
            jira.getIssueType());
    return new JiraPostingService(config);
  }
}
