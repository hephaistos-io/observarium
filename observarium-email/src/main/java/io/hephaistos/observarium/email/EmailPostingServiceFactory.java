package io.hephaistos.observarium.email;

import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.posting.PostingServiceDiagnostics;
import io.hephaistos.observarium.posting.PostingServiceFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link PostingServiceFactory} implementation for the email posting service.
 *
 * <p>Registered via {@code META-INF/services} for {@link java.util.ServiceLoader} discovery.
 */
public class EmailPostingServiceFactory implements PostingServiceFactory {

  private static final Logger log = LoggerFactory.getLogger(EmailPostingServiceFactory.class);
  private static final List<String> ALWAYS_REQUIRED_KEYS = List.of("smtp-host", "from", "to");
  private static final List<String> AUTH_REQUIRED_KEYS = List.of("username", "password");

  @Override
  public String id() {
    return "email";
  }

  @Override
  public Optional<PostingService> create(Map<String, String> config) {
    String enabled = config.getOrDefault("enabled", "false");
    boolean auth = !"false".equalsIgnoreCase(config.get("auth"));
    if (!"true".equalsIgnoreCase(enabled)) {
      List<String> requiredKeys = new ArrayList<>(ALWAYS_REQUIRED_KEYS);
      if (auth) {
        requiredKeys.addAll(AUTH_REQUIRED_KEYS);
      }
      PostingServiceDiagnostics.warnIfConfiguredButNotEnabled(log, id(), config, requiredKeys);
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
            auth,
            startTls);
    return Optional.of(new EmailPostingService(emailConfig));
  }
}
