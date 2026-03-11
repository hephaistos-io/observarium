package io.hephaistos.observarium.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.scrub.ScrubLevel;
import jakarta.enterprise.inject.Instance;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link ObservariumProducer}.
 *
 * <p>Exercises the CDI producer method in isolation using Mockito mocks for the config interface
 * and the CDI {@link Instance} of discovered posting services. The {@code @Inject} fields are
 * populated reflectively to avoid requiring a full CDI container.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ObservariumProducerTest {

  @Mock private ObservariumQuarkusConfig config;
  @Mock private Config mpConfig;

  @SuppressWarnings("unchecked")
  private final Instance<PostingService> emptyInstance = mock(Instance.class);

  private ObservariumProducer producer;

  @BeforeEach
  void setUp() throws Exception {
    producer = new ObservariumProducer();
    injectField(producer, "config", config);
    injectField(producer, "mpConfig", mpConfig);

    when(config.enabled()).thenReturn(true);
    when(config.scrubLevel()).thenReturn("BASIC");
    when(config.traceIdMdcKey()).thenReturn("trace_id");
    when(config.spanIdMdcKey()).thenReturn("span_id");
    when(emptyInstance.iterator()).thenReturn(List.<PostingService>of().iterator());

    // Default: no config properties (SPI factories will get empty maps and return empty)
    when(mpConfig.getPropertyNames()).thenReturn(Set.of());
  }

  @Test
  void observarium_createdWithCorrectScrubLevel_whenEnabled() {
    when(config.scrubLevel()).thenReturn("STRICT");

    Observarium result = producer.observarium(emptyInstance);

    assertThat(result).isNotNull();
    assertThat(result.config().scrubLevel()).isEqualTo(ScrubLevel.STRICT);
  }

  @Test
  void observarium_isMinimal_whenDisabled() {
    when(config.enabled()).thenReturn(false);

    Observarium result = producer.observarium(emptyInstance);

    assertThat(result).isNotNull();
    assertThat(result.config().postingServiceCount()).isZero();
  }

  @Test
  @SuppressWarnings("unchecked")
  void observarium_discoversPostingServices_fromCdiInstance() {
    PostingService stubService = stubPostingService("cdi-stub");
    Instance<PostingService> instance = mock(Instance.class);
    when(instance.iterator()).thenReturn(List.of(stubService).iterator());

    Observarium result = producer.observarium(instance);

    assertThat(result).isNotNull();
    assertThat(result.config().postingServiceCount()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void observarium_discoversPostingServices_viaSpiFactories() {
    // Simulate config for GitHub (the github module is on the test classpath)
    stubMpConfigProperties(
        "observarium.github.enabled", "true",
        "observarium.github.token", "ghp_test",
        "observarium.github.owner", "acme",
        "observarium.github.repo", "backend");

    Observarium result = producer.observarium(emptyInstance);

    assertThat(result).isNotNull();
    assertThat(result.config().postingServiceCount()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void observarium_noSpiServices_whenNoneEnabled() {
    // mpConfig returns no properties (set in @BeforeEach)
    Observarium result = producer.observarium(emptyInstance);

    assertThat(result).isNotNull();
    assertThat(result.config().postingServiceCount()).isZero();
  }

  @Test
  void observarium_usesCustomMdcKeys_fromConfig() {
    when(config.traceIdMdcKey()).thenReturn("X-Trace-Id");
    when(config.spanIdMdcKey()).thenReturn("X-Span-Id");

    Observarium result = producer.observarium(emptyInstance);

    assertThat(result).isNotNull();
    assertThat(result.config().scrubLevel()).isEqualTo(ScrubLevel.BASIC);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static void injectField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private void stubMpConfigProperties(String... keyValuePairs) {
    Set<String> names = new java.util.HashSet<>();
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      String key = keyValuePairs[i];
      String value = keyValuePairs[i + 1];
      names.add(key);
      when(mpConfig.getOptionalValue(key, String.class)).thenReturn(java.util.Optional.of(value));
    }
    when(mpConfig.getPropertyNames()).thenReturn(names);
  }

  private static PostingService stubPostingService(String name) {
    return new PostingService() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
        return DuplicateSearchResult.notFound();
      }

      @Override
      public PostingResult createIssue(ExceptionEvent event) {
        return PostingResult.failure("stub");
      }

      @Override
      public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
        return PostingResult.failure("stub");
      }
    };
  }
}
