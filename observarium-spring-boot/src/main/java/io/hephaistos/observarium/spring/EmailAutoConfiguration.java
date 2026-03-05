package io.hephaistos.observarium.spring;

import io.hephaistos.observarium.email.EmailConfig;
import io.hephaistos.observarium.email.EmailPostingService;
import io.hephaistos.observarium.posting.PostingService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the email {@link PostingService}.
 *
 * <p>Activated only when:
 *
 * <ol>
 *   <li>The {@code observarium-email} module is on the runtime classpath ({@code
 *       EmailPostingService} class is present), and
 *   <li>{@code observarium.email.enabled=true} is set in application properties.
 * </ol>
 */
@AutoConfiguration
@ConditionalOnClass(name = "io.hephaistos.observarium.email.EmailPostingService")
@ConditionalOnProperty(name = "observarium.email.enabled", havingValue = "true")
@EnableConfigurationProperties(ObservariumProperties.class)
public class EmailAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "emailPostingService")
  public PostingService emailPostingService(ObservariumProperties properties) {
    ObservariumProperties.Email email = properties.getEmail();
    EmailConfig config =
        new EmailConfig(
            email.getSmtpHost(),
            email.getSmtpPort(),
            email.getFrom(),
            email.getTo(),
            email.getUsername(),
            email.getPassword(),
            email.isStartTls());
    return new EmailPostingService(config);
  }
}
