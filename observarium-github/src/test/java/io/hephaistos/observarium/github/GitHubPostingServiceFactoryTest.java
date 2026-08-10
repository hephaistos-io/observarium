package io.hephaistos.observarium.github;

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

class GitHubPostingServiceFactoryTest {

  private final GitHubPostingServiceFactory factory = new GitHubPostingServiceFactory();
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
    return (Logger) LoggerFactory.getLogger(GitHubPostingServiceFactory.class);
  }

  @Test
  void id_returnsGithub() {
    assertThat(factory.id()).isEqualTo("github");
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
            "token", "ghp_test",
            "owner", "acme",
            "repo", "backend");
    Optional<PostingService> result = factory.create(config);
    assertThat(result).isPresent();
    assertThat(result.get().name()).isEqualTo("github");
  }

  @Test
  void create_throwsIllegalArgument_whenEnabledButTokenMissing() {
    Map<String, String> config = Map.of("enabled", "true", "owner", "acme", "repo", "backend");
    assertThatThrownBy(() -> factory.create(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("token");
  }

  @Test
  void create_throwsIllegalArgument_whenEnabledButOwnerMissing() {
    Map<String, String> config = Map.of("enabled", "true", "token", "ghp_test", "repo", "backend");
    assertThatThrownBy(() -> factory.create(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("owner");
  }

  @Test
  void create_throwsIllegalArgument_whenEnabledButRepoMissing() {
    Map<String, String> config = Map.of("enabled", "true", "token", "ghp_test", "owner", "acme");
    assertThatThrownBy(() -> factory.create(config))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("repo");
  }

  @Test
  void create_warns_whenConfigCompleteButNotEnabled() {
    Map<String, String> config =
        Map.of(
            "token", "ghp_test",
            "owner", "acme",
            "repo", "backend");
    assertThat(factory.create(config)).isEmpty();
    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains("github")
                    .contains("not enabled")
                    .contains("github.enabled=true")
                    .contains("token")
                    .contains("owner")
                    .contains("repo"));
  }

  @Test
  void create_doesNotWarn_whenConfigAbsent() {
    assertThat(factory.create(Map.of())).isEmpty();
    assertThat(appender.list).isEmpty();
  }

  @Test
  void create_doesNotWarn_whenConfigPartial() {
    Map<String, String> config = Map.of("token", "ghp_test", "owner", "acme");
    assertThat(factory.create(config)).isEmpty();
    assertThat(appender.list).isEmpty();
  }

  @Test
  void create_doesNotWarn_whenEnabled() {
    Map<String, String> config =
        Map.of(
            "enabled", "true",
            "token", "ghp_test",
            "owner", "acme",
            "repo", "backend");
    assertThat(factory.create(config)).isPresent();
    assertThat(appender.list).isEmpty();
  }

  @Test
  void factory_isDiscoverableViaServiceLoader() {
    ServiceLoader<PostingServiceFactory> loader = ServiceLoader.load(PostingServiceFactory.class);
    Optional<PostingServiceFactory> found =
        loader.stream()
            .map(ServiceLoader.Provider::get)
            .filter(f -> "github".equals(f.id()))
            .findFirst();
    assertThat(found).isPresent();
    assertThat(found.get()).isInstanceOf(GitHubPostingServiceFactory.class);
  }
}
