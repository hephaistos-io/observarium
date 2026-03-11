package io.hephaistos.observarium.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.posting.PostingServiceFactory;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class EmailPostingServiceFactoryTest {

  private final EmailPostingServiceFactory factory = new EmailPostingServiceFactory();

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
            "to", "team@example.com");
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
