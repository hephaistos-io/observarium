package io.hephaistos.observarium.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.ObservariumListener;
import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import jakarta.enterprise.inject.Instance;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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
 * Unit tests for {@link ObservariumProducer} covering multi-service composition scenarios via SPI
 * and CDI discovery.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ObservariumProducerMultiServiceTest {

  @Mock private ObservariumQuarkusConfig config;
  @Mock private Config mpConfig;

  @SuppressWarnings("unchecked")
  private final Instance<PostingService> emptyInstance = mock(Instance.class);

  @SuppressWarnings("unchecked")
  private final Instance<ObservariumListener> emptyListenerInstance = mock(Instance.class);

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
    when(config.maxDuplicateComments()).thenReturn(5);
    when(emptyInstance.iterator()).thenReturn(List.<PostingService>of().iterator());
    when(emptyListenerInstance.isResolvable()).thenReturn(false);
    when(mpConfig.getPropertyNames()).thenReturn(Set.of());
  }

  @Test
  void allFourSpiServices_areWired_whenAllEnabledViaConfig() {
    stubAllFourServicesEnabled();

    Observarium result = producer.observarium(emptyInstance, emptyListenerInstance);

    assertThat(result).isNotNull();
    assertThat(result.config().postingServiceCount()).isEqualTo(4);
  }

  @Test
  @SuppressWarnings("unchecked")
  void cdiDiscoveredAndSpiServices_bothContribute() {
    PostingService cdiService = stubPostingService("cdi-discovered");
    Instance<PostingService> instanceWithOne = mock(Instance.class);
    when(instanceWithOne.iterator()).thenReturn(List.of(cdiService).iterator());

    // Enable GitHub via SPI
    stubMpConfigProperties(
        "observarium.github.enabled", "true",
        "observarium.github.token", "ghp_test",
        "observarium.github.owner", "acme",
        "observarium.github.repo", "backend");

    Observarium result = producer.observarium(instanceWithOne, emptyListenerInstance);

    // 1 CDI + at least 1 SPI
    assertThat(result).isNotNull();
    assertThat(result.config().postingServiceCount()).isGreaterThanOrEqualTo(2);
  }

  @Test
  void zeroPostingServices_whenAllDisabled() {
    Observarium result = producer.observarium(emptyInstance, emptyListenerInstance);

    assertThat(result).isNotNull();
    assertThat(result.config().postingServiceCount()).isZero();
  }

  @Test
  void observarium_isCreated_evenWhenObservariumIsDisabled() {
    when(config.enabled()).thenReturn(false);

    Observarium result = producer.observarium(emptyInstance, emptyListenerInstance);

    assertThat(result).isNotNull();
    assertThat(result.config().postingServiceCount()).isZero();
  }

  @Test
  void onlyGitLabAndEmail_whenOnlyThoseTwoEnabled() {
    stubMpConfigProperties(
        "observarium.gitlab.enabled", "true",
        "observarium.gitlab.base-url", "https://gitlab.com",
        "observarium.gitlab.private-token", "glpat-secret",
        "observarium.gitlab.project-id", "42",
        "observarium.email.enabled", "true",
        "observarium.email.smtp-host", "smtp.example.com",
        "observarium.email.from", "alerts@example.com",
        "observarium.email.to", "team@example.com",
        "observarium.email.username", "smtp-user",
        "observarium.email.password", "smtp-pass");

    Observarium result = producer.observarium(emptyInstance, emptyListenerInstance);

    assertThat(result.config().postingServiceCount()).isEqualTo(2);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static void injectField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private void stubAllFourServicesEnabled() {
    stubMpConfigProperties(
        "observarium.github.enabled", "true",
        "observarium.github.token", "ghp_test",
        "observarium.github.owner", "acme",
        "observarium.github.repo", "backend",
        "observarium.jira.enabled", "true",
        "observarium.jira.base-url", "https://acme.atlassian.net",
        "observarium.jira.username", "user@acme.com",
        "observarium.jira.api-token", "jira-secret",
        "observarium.jira.project-key", "OBS",
        "observarium.gitlab.enabled", "true",
        "observarium.gitlab.base-url", "https://gitlab.com",
        "observarium.gitlab.private-token", "glpat-secret",
        "observarium.gitlab.project-id", "42",
        "observarium.email.enabled", "true",
        "observarium.email.smtp-host", "smtp.example.com",
        "observarium.email.from", "alerts@example.com",
        "observarium.email.to", "team@example.com",
        "observarium.email.username", "smtp-user",
        "observarium.email.password", "smtp-pass");
  }

  private void stubMpConfigProperties(String... keyValuePairs) {
    Set<String> names = new HashSet<>();
    // Preserve any existing property names from previous stubs
    Iterable<String> existing = mpConfig.getPropertyNames();
    if (existing != null) {
      for (String name : existing) {
        names.add(name);
      }
    }
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      String key = keyValuePairs[i];
      String value = keyValuePairs[i + 1];
      names.add(key);
      when(mpConfig.getOptionalValue(key, String.class)).thenReturn(Optional.of(value));
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
