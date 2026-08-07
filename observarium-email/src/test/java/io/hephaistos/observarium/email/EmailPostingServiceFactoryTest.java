package io.hephaistos.observarium.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.posting.PostingServiceFactory;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class EmailPostingServiceFactoryTest {

  private final EmailPostingServiceFactory factory = new EmailPostingServiceFactory();
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    appender = new ListAppender<>();
    appender.start();
    logger().addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    logger().detachAppender(appender);
  }

  private static Logger logger() {
    return (Logger) LoggerFactory.getLogger(EmailPostingServiceFactory.class);
  }

  @Test
  void id_returnsEmail() {
    assertThat(factory.id()).isEqualTo("email");
  }

  @Test
  void create_returnsEmpty_whenNotEnabled() {
    assertThat(factory.create(Map.of())).isEmpty();
  }

  @Test
  void create_returnsEmpty_whenEnabledIsFalse() {
    assertThat(factory.create(Map.of("enabled", "false"))).isEmpty();
  }

  @Test
  void create_returnsService_whenEnabledWithValidConfig() {
    Map<String, String> config =
        Map.of(
            "enabled", "true",
            "smtp-host", "smtp.example.com",
            "from", "alerts@example.com",
            "to", "team@example.com",
            "username", "smtp-user",
            "password", "smtp-pass");
    Optional<PostingService> result = factory.create(config);
    assertThat(result).isPresent();
    assertThat(result.get().name()).isEqualTo("email");
  }

  @Test
  void create_throwsIllegalArgument_whenEnabledButSmtpHostMissing() {
    Map<String, String> config =
        Map.of(
            "enabled", "true",
            "from", "alerts@example.com",
            "to", "team@example.com");
    assertThatThrownBy(() -> factory.create(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("smtpHost");
  }

  @Test
  void create_throwsIllegalArgument_whenEnabledButFromMissing() {
    Map<String, String> config =
        Map.of(
            "enabled", "true",
            "smtp-host", "smtp.example.com",
            "to", "team@example.com");
    assertThatThrownBy(() -> factory.create(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("from");
  }

  @Test
  void create_throwsIllegalArgument_whenEnabledButToMissing() {
    Map<String, String> config =
        Map.of(
            "enabled", "true",
            "smtp-host", "smtp.example.com",
            "from", "alerts@example.com");
    assertThatThrownBy(() -> factory.create(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("to");
  }

  @Test
  void create_usesDefaultPortAndStartTls_whenNotSpecified() {
    Map<String, String> config =
        Map.of(
            "enabled", "true",
            "smtp-host", "smtp.example.com",
            "from", "alerts@example.com",
            "to", "team@example.com",
            "auth", "false");
    Optional<PostingService> result = factory.create(config);
    assertThat(result).isPresent();
  }

  @Test
  void create_throwsIllegalArgument_whenSmtpPortIsNotNumeric() {
    Map<String, String> config =
        Map.of(
            "enabled", "true",
            "smtp-host", "smtp.example.com",
            "smtp-port", "abc",
            "from", "alerts@example.com",
            "to", "team@example.com");
    assertThatThrownBy(() -> factory.create(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("smtpPort");
  }

  @Test
  void create_authFalse_allowsMissingCredentials() {
    // When auth=false the factory must not require username/password.
    Map<String, String> config =
        Map.of(
            "enabled", "true",
            "smtp-host", "smtp.example.com",
            "from", "alerts@example.com",
            "to", "team@example.com",
            "auth", "false");
    Optional<PostingService> result = factory.create(config);
    assertThat(result).isPresent();
    assertThat(result.get().name()).isEqualTo("email");
  }

  @Test
  void create_authTrue_throwsWhenCredentialsMissing() {
    // When auth=true (or absent, defaulting to true), missing credentials must throw.
    Map<String, String> config =
        Map.of(
            "enabled", "true",
            "smtp-host", "smtp.example.com",
            "from", "alerts@example.com",
            "to", "team@example.com",
            "auth", "true");
    assertThatThrownBy(() -> factory.create(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("username");
  }

  @Test
  void create_authDefaultsToTrue_whenKeyAbsent() {
    // Missing auth key must behave identically to auth=true.
    Map<String, String> config =
        Map.of(
            "enabled", "true",
            "smtp-host", "smtp.example.com",
            "from", "alerts@example.com",
            "to", "team@example.com");
    assertThatThrownBy(() -> factory.create(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("username");
  }

  @Test
  void create_warns_whenConfigCompleteButNotEnabled() {
    // auth defaults to true, so username/password are part of the required set here.
    Map<String, String> config =
        Map.of(
            "smtp-host", "smtp.example.com",
            "from", "alerts@example.com",
            "to", "team@example.com",
            "username", "smtp-user",
            "password", "smtp-pass");
    assertThat(factory.create(config)).isEmpty();
    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains("email")
                    .contains("not enabled")
                    .contains("email.enabled=true"));
  }

  @Test
  void create_doesNotWarn_whenConfigAbsent() {
    assertThat(factory.create(Map.of())).isEmpty();
    assertThat(appender.list).isEmpty();
  }

  @Test
  void create_doesNotWarn_whenConfigPartial() {
    // auth defaults to true, so username/password are required but missing here — must not warn.
    Map<String, String> config =
        Map.of(
            "smtp-host", "smtp.example.com",
            "from", "alerts@example.com",
            "to", "team@example.com");
    assertThat(factory.create(config)).isEmpty();
    assertThat(appender.list).isEmpty();
  }

  @Test
  void create_doesNotWarn_whenEnabled() {
    Map<String, String> config =
        Map.of(
            "enabled", "true",
            "smtp-host", "smtp.example.com",
            "from", "alerts@example.com",
            "to", "team@example.com",
            "username", "smtp-user",
            "password", "smtp-pass");
    assertThat(factory.create(config)).isPresent();
    assertThat(appender.list).isEmpty();
  }

  @Test
  void create_authFalse_warns_withoutCredentials() {
    // With auth=false, username/password are not part of the required set — the always-required
    // keys alone are enough to trigger the warning.
    Map<String, String> config =
        Map.of(
            "smtp-host", "smtp.example.com",
            "from", "alerts@example.com",
            "to", "team@example.com",
            "auth", "false");
    assertThat(factory.create(config)).isEmpty();
    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains("email")
                    .contains("not enabled")
                    .doesNotContain("username")
                    .doesNotContain("password"));
  }

  @Test
  void create_authTrue_doesNotWarn_withoutCredentials() {
    // With auth=true (explicit), username/password are required — missing them must not warn.
    Map<String, String> config =
        Map.of(
            "smtp-host", "smtp.example.com",
            "from", "alerts@example.com",
            "to", "team@example.com",
            "auth", "true");
    assertThat(factory.create(config)).isEmpty();
    assertThat(appender.list).isEmpty();
  }

  @Test
  void factory_isDiscoverableViaServiceLoader() {
    ServiceLoader<PostingServiceFactory> loader = ServiceLoader.load(PostingServiceFactory.class);
    Optional<PostingServiceFactory> found =
        loader.stream()
            .map(ServiceLoader.Provider::get)
            .filter(f -> "email".equals(f.id()))
            .findFirst();
    assertThat(found).isPresent();
    assertThat(found.get()).isInstanceOf(EmailPostingServiceFactory.class);
  }
}
