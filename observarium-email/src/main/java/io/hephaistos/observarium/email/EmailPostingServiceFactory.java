package io.hephaistos.observarium.email;

import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.posting.PostingServiceFactory;
import java.util.Map;
import java.util.Optional;

/**
 * {@link PostingServiceFactory} implementation for the email posting service.
 *
 * <p>Registered via {@code META-INF/services} for {@link java.util.ServiceLoader} discovery.
 */
public class EmailPostingServiceFactory implements PostingServiceFactory {

  @Override
  public String id() {
    return "email";
  }

  @Override
  public Optional<PostingService> create(Map<String, String> config) {
    String enabled = config.getOrDefault("enabled", "false");
    if (!"true".equalsIgnoreCase(enabled)) {
      return Optional.empty();
    }
    int smtpPort = 587;
    String portStr = config.get("smtp-port");
    if (portStr != null && !portStr.isBlank()) {
      try {
        smtpPort = Integer.parseInt(portStr);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
            "EmailConfig.smtpPort must be a valid integer, got: " + portStr, e);
      }
    }
    boolean startTls = !"false".equalsIgnoreCase(config.get("start-tls"));
    EmailConfig emailConfig =
        new EmailConfig(
            config.get("smtp-host"),
            smtpPort,
            config.get("from"),
            config.get("to"),
            config.get("username"),
            config.get("password"),
            startTls);
    return Optional.of(new EmailPostingService(emailConfig));
  }
}
