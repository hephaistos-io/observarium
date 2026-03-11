package io.hephaistos.observarium.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.posting.PostingServiceFactory;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class JiraPostingServiceFactoryTest {

  private final JiraPostingServiceFactory factory = new JiraPostingServiceFactory();

  @Test
  void id_returnsJira() {
    assertThat(factory.id()).isEqualTo("jira");
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
            "base-url", "https://acme.atlassian.net",
            "username", "user@acme.com",
            "api-token", "jira-secret",
            "project-key", "OBS");
    Optional<PostingService> result = factory.create(config);
    assertThat(result).isPresent();
    assertThat(result.get().name()).isEqualTo("jira");
  }

  @Test
  void create_throwsIllegalArgument_whenEnabledButBaseUrlMissing() {
    Map<String, String> config =
        Map.of(
            "enabled", "true",
            "username", "user@acme.com",
            "api-token", "jira-secret",
            "project-key", "OBS");
    assertThatThrownBy(() -> factory.create(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("baseUrl");
  }

  @Test
  void create_throwsIllegalArgument_whenEnabledButUsernameMissing() {
    Map<String, String> config =
        Map.of(
            "enabled", "true",
            "base-url", "https://acme.atlassian.net",
            "api-token", "jira-secret",
            "project-key", "OBS");
    assertThatThrownBy(() -> factory.create(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("username");
  }

  @Test
  void create_throwsIllegalArgument_whenEnabledButApiTokenMissing() {
    Map<String, String> config =
        Map.of(
            "enabled", "true",
            "base-url", "https://acme.atlassian.net",
            "username", "user@acme.com",
            "project-key", "OBS");
    assertThatThrownBy(() -> factory.create(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("apiToken");
  }

  @Test
  void create_throwsIllegalArgument_whenEnabledButProjectKeyMissing() {
    Map<String, String> config =
        Map.of(
            "enabled", "true",
            "base-url", "https://acme.atlassian.net",
            "username", "user@acme.com",
            "api-token", "jira-secret");
    assertThatThrownBy(() -> factory.create(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("projectKey");
  }

  @Test
  void factory_isDiscoverableViaServiceLoader() {
    ServiceLoader<PostingServiceFactory> loader = ServiceLoader.load(PostingServiceFactory.class);
    Optional<PostingServiceFactory> found =
        loader.stream()
            .map(ServiceLoader.Provider::get)
            .filter(f -> "jira".equals(f.id()))
            .findFirst();
    assertThat(found).isPresent();
    assertThat(found.get()).isInstanceOf(JiraPostingServiceFactory.class);
  }
}
