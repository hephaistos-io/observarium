package io.hephaistos.observarium.gitlab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.posting.PostingServiceFactory;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;

class GitLabPostingServiceFactoryTest {

  private final GitLabPostingServiceFactory factory = new GitLabPostingServiceFactory();

  @Test
  void id_returnsGitlab() {
    assertThat(factory.id()).isEqualTo("gitlab");
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
            "base-url", "https://gitlab.com",
            "private-token", "glpat-secret",
            "project-id", "42");
    Optional<PostingService> result = factory.create(config);
    assertThat(result).isPresent();
    assertThat(result.get().name()).isEqualTo("gitlab");
  }

  @Test
  void create_throwsIllegalArgument_whenEnabledButBaseUrlMissing() {
    Map<String, String> config =
        Map.of("enabled", "true", "private-token", "glpat-secret", "project-id", "42");
    assertThatThrownBy(() -> factory.create(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("baseUrl");
  }

  @Test
  void create_throwsIllegalArgument_whenEnabledButPrivateTokenMissing() {
    Map<String, String> config =
        Map.of("enabled", "true", "base-url", "https://gitlab.com", "project-id", "42");
    assertThatThrownBy(() -> factory.create(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("privateToken");
  }

  @Test
  void create_throwsIllegalArgument_whenEnabledButProjectIdMissing() {
    Map<String, String> config =
        Map.of(
            "enabled", "true", "base-url", "https://gitlab.com", "private-token", "glpat-secret");
    assertThatThrownBy(() -> factory.create(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("projectId");
  }

  @Test
  void factory_isDiscoverableViaServiceLoader() {
    ServiceLoader<PostingServiceFactory> loader = ServiceLoader.load(PostingServiceFactory.class);
    Optional<PostingServiceFactory> found =
        loader.stream()
            .map(ServiceLoader.Provider::get)
            .filter(f -> "gitlab".equals(f.id()))
            .findFirst();
    assertThat(found).isPresent();
    assertThat(found.get()).isInstanceOf(GitLabPostingServiceFactory.class);
  }
}
