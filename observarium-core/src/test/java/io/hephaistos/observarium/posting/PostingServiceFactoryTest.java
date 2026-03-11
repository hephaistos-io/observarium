package io.hephaistos.observarium.posting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.hephaistos.observarium.event.ExceptionEvent;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the {@link PostingServiceFactory} SPI using an inline test implementation.
 *
 * <p>Verifies the expected contract: returns empty when disabled, returns a service when enabled,
 * and throws when enabled but missing required config.
 */
class PostingServiceFactoryTest {

  private final PostingServiceFactory factory = new TestPostingServiceFactory();

  @Test
  void id_returnsNonEmptyIdentifier() {
    assertThat(factory.id()).isEqualTo("test");
  }

  @Test
  void create_returnsEmpty_whenEnabledKeyIsAbsent() {
    Optional<PostingService> result = factory.create(Map.of());
    assertThat(result).isEmpty();
  }

  @Test
  void create_returnsEmpty_whenEnabledIsFalse() {
    Optional<PostingService> result = factory.create(Map.of("enabled", "false"));
    assertThat(result).isEmpty();
  }

  @Test
  void create_returnsService_whenEnabledAndConfigValid() {
    Optional<PostingService> result =
        factory.create(Map.of("enabled", "true", "api-key", "secret"));
    assertThat(result).isPresent();
    assertThat(result.get().name()).isEqualTo("test");
  }

  @Test
  void create_throwsIllegalArgument_whenEnabledButMissingRequiredConfig() {
    assertThatThrownBy(() -> factory.create(Map.of("enabled", "true")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("api-key");
  }

  /**
   * Inline test implementation of {@link PostingServiceFactory} that requires an {@code api-key}
   * config entry when enabled.
   */
  private static final class TestPostingServiceFactory implements PostingServiceFactory {

    @Override
    public String id() {
      return "test";
    }

    @Override
    public Optional<PostingService> create(Map<String, String> config) {
      String enabled = config.getOrDefault("enabled", "false");
      if (!"true".equalsIgnoreCase(enabled)) {
        return Optional.empty();
      }
      String apiKey = config.get("api-key");
      if (apiKey == null || apiKey.isBlank()) {
        throw new IllegalArgumentException(
            "api-key is required when test posting service is enabled");
      }
      return Optional.of(
          new PostingService() {
            @Override
            public String name() {
              return "test";
            }

            @Override
            public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
              return DuplicateSearchResult.notFound();
            }

            @Override
            public PostingResult createIssue(ExceptionEvent event) {
              return PostingResult.failure("test-stub");
            }

            @Override
            public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
              return PostingResult.failure("test-stub");
            }
          });
    }
  }
}
