package io.hephaistos.observarium.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.Optional;
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
 * and the CDI {@link Instance} of discovered posting services. The {@code @Inject} field is
 * populated reflectively to avoid requiring a full CDI container.
 *
 * <p>{@link Strictness#LENIENT} is used because the {@code @BeforeEach} sets up common stubs for
 * all config methods; tests that disable Observarium intentionally do not consume stubs like {@code
 * scrubLevel()} or {@code traceIdMdcKey()}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ObservariumProducerTest {

  @Mock private ObservariumQuarkusConfig config;

  @SuppressWarnings("unchecked")
  private final Instance<PostingService> emptyInstance = mock(Instance.class);

  private ObservariumProducer producer;

  @BeforeEach
  void setUp() throws Exception {
    producer = new ObservariumProducer();
    injectConfig(producer, config);

    // Default stubs — individual tests override as needed.
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
  // Enabled / disabled
  // ---------------------------------------------------------------------------

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

  // ---------------------------------------------------------------------------
  // CDI-discovered posting services
  // ---------------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void observarium_discoversPostingServices_fromCdiInstance() {
    PostingService stubService = stubPostingService("cdi-stub");
    Instance<PostingService> instance = mock(Instance.class);
    when(instance.iterator()).thenReturn(List.of(stubService).iterator());

    Observarium result = producer.observarium(instance);

    assertThat(result).isNotNull();
    assertThat(result.config().postingServiceCount()).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // Posting services created from config
  // ---------------------------------------------------------------------------

  @Test
  void observarium_createsGitHubService_fromConfig() {
    ObservariumQuarkusConfig.GitHub gh = mock(ObservariumQuarkusConfig.GitHub.class);
    when(gh.enabled()).thenReturn(true);
    when(gh.token()).thenReturn(Optional.of("ghp_test_token"));
    when(gh.owner()).thenReturn(Optional.of("acme"));
    when(gh.repo()).thenReturn(Optional.of("backend"));
    when(gh.labelPrefix()).thenReturn("observarium");
    when(gh.baseUrl()).thenReturn(Optional.empty());
    when(config.github()).thenReturn(Optional.of(gh));

    Observarium result = producer.observarium(emptyInstance);

    assertThat(result).isNotNull();
    assertThat(result.config().postingServiceCount()).isEqualTo(1);
  }

  @Test
  void observarium_createsJiraService_fromConfig() {
    ObservariumQuarkusConfig.Jira jira = mock(ObservariumQuarkusConfig.Jira.class);
    when(jira.enabled()).thenReturn(true);
    when(jira.baseUrl()).thenReturn(Optional.of("https://acme.atlassian.net"));
    when(jira.username()).thenReturn(Optional.of("user@acme.com"));
    when(jira.apiToken()).thenReturn(Optional.of("jira-secret"));
    when(jira.projectKey()).thenReturn(Optional.of("OBS"));
    when(jira.issueType()).thenReturn("Bug");
    when(config.jira()).thenReturn(Optional.of(jira));

    Observarium result = producer.observarium(emptyInstance);

    assertThat(result).isNotNull();
    assertThat(result.config().postingServiceCount()).isEqualTo(1);
  }

  @Test
  void observarium_createsGitLabService_fromConfig() {
    ObservariumQuarkusConfig.GitLab gl = mock(ObservariumQuarkusConfig.GitLab.class);
    when(gl.enabled()).thenReturn(true);
    when(gl.baseUrl()).thenReturn(Optional.of("https://gitlab.com"));
    when(gl.privateToken()).thenReturn(Optional.of("glpat-secret"));
    when(gl.projectId()).thenReturn(Optional.of("42"));
    when(config.gitlab()).thenReturn(Optional.of(gl));

    Observarium result = producer.observarium(emptyInstance);

    assertThat(result).isNotNull();
    assertThat(result.config().postingServiceCount()).isEqualTo(1);
  }

  @Test
  void observarium_createsEmailService_fromConfig() {
    ObservariumQuarkusConfig.Email em = mock(ObservariumQuarkusConfig.Email.class);
    when(em.enabled()).thenReturn(true);
    when(em.smtpHost()).thenReturn(Optional.of("smtp.example.com"));
    when(em.smtpPort()).thenReturn(587);
    when(em.from()).thenReturn(Optional.of("alerts@example.com"));
    when(em.to()).thenReturn(Optional.of("team@example.com"));
    when(em.username()).thenReturn(Optional.empty());
    when(em.password()).thenReturn(Optional.empty());
    when(em.startTls()).thenReturn(true);
    when(config.email()).thenReturn(Optional.of(em));

    Observarium result = producer.observarium(emptyInstance);

    assertThat(result).isNotNull();
    assertThat(result.config().postingServiceCount()).isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // Missing required fields throw IllegalStateException
  // ---------------------------------------------------------------------------

  @Test
  void observarium_throwsIllegalState_whenGithubEnabled_butTokenMissing() {
    ObservariumQuarkusConfig.GitHub gh = mock(ObservariumQuarkusConfig.GitHub.class);
    when(gh.enabled()).thenReturn(true);
    when(gh.token()).thenReturn(Optional.empty());
    when(config.github()).thenReturn(Optional.of(gh));

    assertThatThrownBy(() -> producer.observarium(emptyInstance))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("observarium.github.token is required");
  }

  @Test
  void observarium_throwsIllegalState_whenJiraEnabled_butBaseUrlMissing() {
    ObservariumQuarkusConfig.Jira jira = mock(ObservariumQuarkusConfig.Jira.class);
    when(jira.enabled()).thenReturn(true);
    when(jira.baseUrl()).thenReturn(Optional.empty());
    when(config.jira()).thenReturn(Optional.of(jira));

    assertThatThrownBy(() -> producer.observarium(emptyInstance))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("observarium.jira.base-url is required");
  }

  @Test
  void observarium_throwsIllegalState_whenGithubEnabled_butOwnerMissing() {
    ObservariumQuarkusConfig.GitHub gh = mock(ObservariumQuarkusConfig.GitHub.class);
    when(gh.enabled()).thenReturn(true);
    when(gh.token()).thenReturn(Optional.of("ghp_test_token"));
    when(gh.owner()).thenReturn(Optional.empty());
    when(config.github()).thenReturn(Optional.of(gh));

    assertThatThrownBy(() -> producer.observarium(emptyInstance))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("observarium.github.owner is required");
  }

  @Test
  void observarium_throwsIllegalState_whenGitLabEnabled_butBaseUrlMissing() {
    ObservariumQuarkusConfig.GitLab gl = mock(ObservariumQuarkusConfig.GitLab.class);
    when(gl.enabled()).thenReturn(true);
    when(gl.baseUrl()).thenReturn(Optional.empty());
    when(config.gitlab()).thenReturn(Optional.of(gl));

    assertThatThrownBy(() -> producer.observarium(emptyInstance))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("observarium.gitlab.base-url is required");
  }

  @Test
  void observarium_throwsIllegalState_whenEmailEnabled_butSmtpHostMissing() {
    ObservariumQuarkusConfig.Email em = mock(ObservariumQuarkusConfig.Email.class);
    when(em.enabled()).thenReturn(true);
    when(em.smtpHost()).thenReturn(Optional.empty());
    when(config.email()).thenReturn(Optional.of(em));

    assertThatThrownBy(() -> producer.observarium(emptyInstance))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("observarium.email.smtp-host is required");
  }

  @Test
  void observarium_throwsIllegalState_whenGithubEnabled_butRepoMissing() {
    ObservariumQuarkusConfig.GitHub gh = mock(ObservariumQuarkusConfig.GitHub.class);
    when(gh.enabled()).thenReturn(true);
    when(gh.token()).thenReturn(Optional.of("ghp_test_token"));
    when(gh.owner()).thenReturn(Optional.of("acme"));
    when(gh.repo()).thenReturn(Optional.empty());
    when(config.github()).thenReturn(Optional.of(gh));

    assertThatThrownBy(() -> producer.observarium(emptyInstance))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("observarium.github.repo is required");
  }

  @Test
  void observarium_throwsIllegalState_whenJiraEnabled_butUsernameMissing() {
    ObservariumQuarkusConfig.Jira jira = mock(ObservariumQuarkusConfig.Jira.class);
    when(jira.enabled()).thenReturn(true);
    when(jira.baseUrl()).thenReturn(Optional.of("https://acme.atlassian.net"));
    when(jira.username()).thenReturn(Optional.empty());
    when(config.jira()).thenReturn(Optional.of(jira));

    assertThatThrownBy(() -> producer.observarium(emptyInstance))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("observarium.jira.username is required");
  }

  @Test
  void observarium_throwsIllegalState_whenGitLabEnabled_butPrivateTokenMissing() {
    ObservariumQuarkusConfig.GitLab gl = mock(ObservariumQuarkusConfig.GitLab.class);
    when(gl.enabled()).thenReturn(true);
    when(gl.baseUrl()).thenReturn(Optional.of("https://gitlab.com"));
    when(gl.privateToken()).thenReturn(Optional.empty());
    when(config.gitlab()).thenReturn(Optional.of(gl));

    assertThatThrownBy(() -> producer.observarium(emptyInstance))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("observarium.gitlab.private-token is required");
  }

  @Test
  void observarium_throwsIllegalState_whenEmailEnabled_butFromMissing() {
    ObservariumQuarkusConfig.Email em = mock(ObservariumQuarkusConfig.Email.class);
    when(em.enabled()).thenReturn(true);
    when(em.smtpHost()).thenReturn(Optional.of("smtp.example.com"));
    when(em.smtpPort()).thenReturn(587);
    when(em.from()).thenReturn(Optional.empty());
    when(config.email()).thenReturn(Optional.of(em));

    assertThatThrownBy(() -> producer.observarium(emptyInstance))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("observarium.email.from is required");
  }

  // ---------------------------------------------------------------------------
  // Custom MDC keys
  // ---------------------------------------------------------------------------

  @Test
  void observarium_usesCustomMdcKeys_fromConfig() {
    when(config.traceIdMdcKey()).thenReturn("X-Trace-Id");
    when(config.spanIdMdcKey()).thenReturn("X-Span-Id");

    // The MdcTraceContextProvider reads from MDC at capture time; we verify that the producer
    // completes successfully and returns a non-null Observarium with the expected scrub level —
    // the custom MDC keys are embedded inside the provider and not exposed on ObservariumConfig.
    Observarium result = producer.observarium(emptyInstance);

    assertThat(result).isNotNull();
    assertThat(result.config().scrubLevel()).isEqualTo(ScrubLevel.BASIC);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /** Injects {@code value} into the package-private {@code config} field via reflection. */
  private static void injectConfig(ObservariumProducer target, ObservariumQuarkusConfig value)
      throws Exception {
    Field field = ObservariumProducer.class.getDeclaredField("config");
    field.setAccessible(true);
    field.set(target, value);
  }

  /**
   * Returns a minimal {@link PostingService} stub with the given name. Anonymous inner classes are
   * used instead of Mockito mocks to avoid accidental interaction recording inside the CDI
   * iterator.
   */
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
