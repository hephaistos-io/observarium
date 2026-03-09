package io.hephaistos.observarium.spring;

import io.hephaistos.observarium.github.GitHubConfig;
import io.hephaistos.observarium.github.GitHubPostingService;
import io.hephaistos.observarium.posting.PostingService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the GitHub {@link PostingService}.
 *
 * <p>Activated only when:
 *
 * <ol>
 *   <li>The {@code observarium-github} module is on the runtime classpath ({@code
 *       GitHubPostingService} class is present), and
 *   <li>{@code observarium.github.enabled=true} is set in application properties.
 * </ol>
 *
 * <p>The {@link ConditionalOnClass} guard prevents this configuration class from being loaded when
 * the GitHub module jar is absent, even though it references GitHub classes directly. This works
 * because Spring's condition evaluation happens before class loading of the {@link Bean} method
 * bodies.
 */
@AutoConfiguration
@ConditionalOnClass(name = "io.hephaistos.observarium.github.GitHubPostingService")
@ConditionalOnProperty(name = "observarium.github.enabled", havingValue = "true")
@EnableConfigurationProperties(ObservariumProperties.class)
public class GitHubAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "gitHubPostingService")
  public PostingService gitHubPostingService(ObservariumProperties properties) {
    ObservariumProperties.GitHub github = properties.getGithub();
    GitHubConfig config =
        new GitHubConfig(
            github.getToken(),
            github.getOwner(),
            github.getRepo(),
            github.getLabelPrefix(),
            github.getBaseUrl());
    return new GitHubPostingService(config);
  }
}
