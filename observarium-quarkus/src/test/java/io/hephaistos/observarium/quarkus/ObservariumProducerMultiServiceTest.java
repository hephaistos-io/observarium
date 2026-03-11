package io.hephaistos.observarium.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import jakarta.enterprise.inject.Instance;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link ObservariumProducer} covering multi-service composition scenarios.
 *
 * <p>Verifies that the producer correctly assembles {@link Observarium} when:
 *
 * <ul>
 *   <li>All four posting services are enabled simultaneously via config.
 *   <li>Services come from both CDI discovery and config-based construction.
 *   <li>No services are enabled — {@link Observarium} is still created but with zero services.
 * </ul>
 *
 * <p>Mirrors the pattern of Spring Boot's {@code MultiplePostingServicesAutoConfigurationTest},
 * adapted for the Quarkus CDI producer using plain Mockito mocks.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ObservariumProducerMultiServiceTest {

  @Mock private ObservariumQuarkusConfig config;

  @SuppressWarnings("unchecked")
  private final Instance<PostingService> emptyInstance = mock(Instance.class);

  private ObservariumProducer producer;

  @BeforeEach
  void setUp() throws Exception {
    producer = new ObservariumProducer();
    injectConfig(producer, config);

    // Shared stubs — individual tests override posting-service sections as needed.
    when(config.enabled()).thenReturn(true);
    when(config.scrubLevel()).thenReturn("BASIC");
    when(config.traceIdMdcKey()).thenReturn("trace_id");
    when(config.spanIdMdcKey()).thenReturn("span_id");
    when(config.github()).thenReturn(Optional.empty());
    when(config.jira()).thenReturn(Optional.empty());
    when(config.gitlab()).thenReturn(Optional.empty());
    when(config.email()).thenReturn(Optional.empty());
    when(emptyInstance.iterator()).thenReturn(List.<PostingService>of().iterator());
  }

  // ---------------------------------------------------------------------------
  // All four config-based services enabled simultaneously
  // ---------------------------------------------------------------------------

  @Test
  void allFourPostingServices_areWired_whenAllEnabledViaConfig() {
    stubAllFourServicesEnabled();

    Observarium result = producer.observarium(emptyInstance);

    assertThat(result).isNotNull();
    assertThat(result.config().postingServiceCount()).isEqualTo(4);
  }

  // ---------------------------------------------------------------------------
  // Mix of CDI-discovered and config-based services
  // ---------------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void cdiDiscoveredAndConfigBasedServices_bothContribute() {
    // One service discovered via CDI
    PostingService cdiService = stubPostingService("cdi-discovered");
    Instance<PostingService> instanceWithOne = mock(Instance.class);
    when(instanceWithOne.iterator()).thenReturn(List.of(cdiService).iterator());

    // One service created from config (GitHub)
    ObservariumQuarkusConfig.GitHub gh = stubGitHubConfig();
    when(config.github()).thenReturn(Optional.of(gh));

    Observarium result = producer.observarium(instanceWithOne);

    // Total = 1 CDI + 1 config = 2
    assertThat(result).isNotNull();
    assertThat(result.config().postingServiceCount()).isEqualTo(2);
  }

  @Test
  @SuppressWarnings("unchecked")
  void twoCdiDiscoveredServices_andTwoConfigServices_sum_toFour() {
    PostingService cdiService1 = stubPostingService("cdi-alpha");
    PostingService cdiService2 = stubPostingService("cdi-beta");
    Instance<PostingService> instanceWithTwo = mock(Instance.class);
    when(instanceWithTwo.iterator()).thenReturn(List.of(cdiService1, cdiService2).iterator());

    // Two services from config (GitHub + Jira)
    ObservariumQuarkusConfig.GitHub gh = stubGitHubConfig();
    when(config.github()).thenReturn(Optional.of(gh));

    ObservariumQuarkusConfig.Jira jira = stubJiraConfig();
    when(config.jira()).thenReturn(Optional.of(jira));

    Observarium result = producer.observarium(instanceWithTwo);

    assertThat(result.config().postingServiceCount()).isEqualTo(4);
  }

  // ---------------------------------------------------------------------------
  // Zero services when all disabled
  // ---------------------------------------------------------------------------

  @Test
  void zeroPostingServices_whenAllDisabled() {
    // All config posting services return Optional.empty() (set in @BeforeEach).
    // CDI instance also returns empty (emptyInstance).

    Observarium result = producer.observarium(emptyInstance);

    assertThat(result).isNotNull();
    assertThat(result.config().postingServiceCount()).isZero();
  }

  @Test
  void observarium_isCreated_evenWhenObservariumIsDisabled() {
    when(config.enabled()).thenReturn(false);

    Observarium result = producer.observarium(emptyInstance);

    assertThat(result).isNotNull();
    assertThat(result.config().postingServiceCount()).isZero();
  }

  // ---------------------------------------------------------------------------
  // Subset combinations
  // ---------------------------------------------------------------------------

  @Test
  void onlyGitLabAndEmail_whenOnlyThoseTwoEnabled() {
    ObservariumQuarkusConfig.GitLab gl = stubGitLabConfig();
    when(config.gitlab()).thenReturn(Optional.of(gl));

    ObservariumQuarkusConfig.Email em = stubEmailConfig();
    when(config.email()).thenReturn(Optional.of(em));

    Observarium result = producer.observarium(emptyInstance);

    assertThat(result.config().postingServiceCount()).isEqualTo(2);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static void injectConfig(ObservariumProducer target, ObservariumQuarkusConfig value)
      throws Exception {
    Field field = ObservariumProducer.class.getDeclaredField("config");
    field.setAccessible(true);
    field.set(target, value);
  }

  private void stubAllFourServicesEnabled() {
    // Create sub-mocks first to avoid nested when() calls inside thenReturn() arguments.
    ObservariumQuarkusConfig.GitHub gh = stubGitHubConfig();
    ObservariumQuarkusConfig.Jira jira = stubJiraConfig();
    ObservariumQuarkusConfig.GitLab gl = stubGitLabConfig();
    ObservariumQuarkusConfig.Email em = stubEmailConfig();
    when(config.github()).thenReturn(Optional.of(gh));
    when(config.jira()).thenReturn(Optional.of(jira));
    when(config.gitlab()).thenReturn(Optional.of(gl));
    when(config.email()).thenReturn(Optional.of(em));
  }

  private ObservariumQuarkusConfig.GitHub stubGitHubConfig() {
    ObservariumQuarkusConfig.GitHub gh = mock(ObservariumQuarkusConfig.GitHub.class);
    when(gh.enabled()).thenReturn(true);
    when(gh.token()).thenReturn(Optional.of("ghp_test_token"));
    when(gh.owner()).thenReturn(Optional.of("acme"));
    when(gh.repo()).thenReturn(Optional.of("backend"));
    when(gh.labelPrefix()).thenReturn("observarium");
    when(gh.baseUrl()).thenReturn(Optional.empty());
    return gh;
  }

  private ObservariumQuarkusConfig.Jira stubJiraConfig() {
    ObservariumQuarkusConfig.Jira jira = mock(ObservariumQuarkusConfig.Jira.class);
    when(jira.enabled()).thenReturn(true);
    when(jira.baseUrl()).thenReturn(Optional.of("https://acme.atlassian.net"));
    when(jira.username()).thenReturn(Optional.of("user@acme.com"));
    when(jira.apiToken()).thenReturn(Optional.of("jira-secret"));
    when(jira.projectKey()).thenReturn(Optional.of("OBS"));
    when(jira.issueType()).thenReturn("Bug");
    return jira;
  }

  private ObservariumQuarkusConfig.GitLab stubGitLabConfig() {
    ObservariumQuarkusConfig.GitLab gl = mock(ObservariumQuarkusConfig.GitLab.class);
    when(gl.enabled()).thenReturn(true);
    when(gl.baseUrl()).thenReturn(Optional.of("https://gitlab.com"));
    when(gl.privateToken()).thenReturn(Optional.of("glpat-secret"));
    when(gl.projectId()).thenReturn(Optional.of("42"));
    return gl;
  }

  private ObservariumQuarkusConfig.Email stubEmailConfig() {
    ObservariumQuarkusConfig.Email em = mock(ObservariumQuarkusConfig.Email.class);
    when(em.enabled()).thenReturn(true);
    when(em.smtpHost()).thenReturn(Optional.of("smtp.example.com"));
    when(em.smtpPort()).thenReturn(587);
    when(em.from()).thenReturn(Optional.of("alerts@example.com"));
    when(em.to()).thenReturn(Optional.of("team@example.com"));
    when(em.username()).thenReturn(Optional.empty());
    when(em.password()).thenReturn(Optional.empty());
    when(em.startTls()).thenReturn(true);
    return em;
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
