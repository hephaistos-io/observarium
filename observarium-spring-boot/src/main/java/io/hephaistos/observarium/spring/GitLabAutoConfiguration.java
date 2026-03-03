package io.hephaistos.observarium.spring;

import io.hephaistos.observarium.gitlab.GitLabConfig;
import io.hephaistos.observarium.gitlab.GitLabPostingService;
import io.hephaistos.observarium.posting.PostingService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the GitLab {@link PostingService}.
 *
 * <p>Activated only when:
 * <ol>
 *   <li>The {@code observarium-gitlab} module is on the runtime classpath
 *       ({@code GitLabPostingService} class is present), and</li>
 *   <li>{@code observarium.gitlab.enabled=true} is set in application properties.</li>
 * </ol>
 */
@AutoConfiguration
@ConditionalOnClass(name = "io.hephaistos.observarium.gitlab.GitLabPostingService")
@ConditionalOnProperty(name = "observarium.gitlab.enabled", havingValue = "true")
@EnableConfigurationProperties(ObservariumProperties.class)
public class GitLabAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "gitLabPostingService")
    public PostingService gitLabPostingService(ObservariumProperties properties) {
        ObservariumProperties.GitLab gitlab = properties.getGitlab();
        GitLabConfig config = new GitLabConfig(
                gitlab.getBaseUrl(),
                gitlab.getPrivateToken(),
                gitlab.getProjectId()
        );
        return new GitLabPostingService(config);
    }
}
