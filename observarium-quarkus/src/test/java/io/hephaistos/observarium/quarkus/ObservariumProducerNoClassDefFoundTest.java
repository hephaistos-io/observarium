package io.hephaistos.observarium.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.posting.PostingService;
import jakarta.enterprise.inject.Instance;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;

/**
 * Tests for {@link ObservariumProducer}'s graceful degradation when a posting service module is
 * absent from the classpath at runtime.
 *
 * <p>The producer wraps each config-based service instantiation in a {@code try/catch
 * (NoClassDefFoundError)}. These tests exercise the <em>actual production catch blocks</em> by
 * subclassing the producer and overriding the package-private factory methods ({@code
 * createGitHubService}, etc.) to throw {@link NoClassDefFoundError}, simulating a missing module
 * JAR.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ObservariumProducerNoClassDefFoundTest {

  @Mock private ObservariumQuarkusConfig config;
  @Mock private ObservariumQuarkusConfig.GitHub githubConfig;

  @SuppressWarnings("unchecked")
  private final Instance<PostingService> emptyInstance = mock(Instance.class);

  private ListAppender<ILoggingEvent> logAppender;
  private Logger producerLogger;

  @BeforeEach
  void setUp() throws Exception {
    when(config.enabled()).thenReturn(true);
    when(config.scrubLevel()).thenReturn("BASIC");
    when(config.traceIdMdcKey()).thenReturn("trace_id");
    when(config.spanIdMdcKey()).thenReturn("span_id");
    when(config.github()).thenReturn(Optional.empty());
    when(config.jira()).thenReturn(Optional.empty());
    when(config.gitlab()).thenReturn(Optional.empty());
    when(config.email()).thenReturn(Optional.empty());
    when(emptyInstance.iterator()).thenReturn(List.<PostingService>of().iterator());

    producerLogger =
        (Logger) LoggerFactory.getLogger("io.hephaistos.observarium.quarkus.ObservariumProducer");
    logAppender = new ListAppender<>();
    logAppender.start();
    producerLogger.addAppender(logAppender);
    producerLogger.setLevel(Level.WARN);
  }

  @AfterEach
  void tearDown() {
    producerLogger.detachAppender(logAppender);
    logAppender.stop();
  }

  // ---------------------------------------------------------------------------
  // Graceful degradation: NoClassDefFoundError is caught, Observarium still created
  // ---------------------------------------------------------------------------

  @Test
  void observarium_isCreated_whenGitHubModuleIsMissing() throws Exception {
    enableGitHubConfig();
    NcdfeThrowingProducer producer = new NcdfeThrowingProducer();
    injectConfig(producer, config);

    Observarium result = producer.observarium(emptyInstance);

    assertThat(result)
        .as("Observarium must be created even when GitHub module is missing")
        .isNotNull();
    assertThat(result.config().postingServiceCount())
        .as("GitHub service should not be present when its module throws NCDFE")
        .isZero();
  }

  @Test
  void producer_emitsWarning_whenGitHubModuleIsMissing() throws Exception {
    enableGitHubConfig();
    NcdfeThrowingProducer producer = new NcdfeThrowingProducer();
    injectConfig(producer, config);

    producer.observarium(emptyInstance);

    List<ILoggingEvent> warnings =
        logAppender.list.stream()
            .filter(e -> e.getLevel() == Level.WARN)
            .filter(e -> e.getFormattedMessage().contains("observarium-github"))
            .toList();
    assertThat(warnings)
        .as("Production catch block must emit a WARN mentioning 'observarium-github'")
        .isNotEmpty();
  }

  @Test
  void producer_continuesCreatingOtherServices_afterOneThrowsNcdfe() throws Exception {
    enableGitHubConfig();

    // Enable Jira too — the NCDFE-throwing producer only fails GitHub,
    // so Jira should still be created normally.
    ObservariumQuarkusConfig.Jira jiraConfig = mock(ObservariumQuarkusConfig.Jira.class);
    when(jiraConfig.enabled()).thenReturn(true);
    when(jiraConfig.baseUrl()).thenReturn(Optional.of("https://acme.atlassian.net"));
    when(jiraConfig.username()).thenReturn(Optional.of("user@acme.com"));
    when(jiraConfig.apiToken()).thenReturn(Optional.of("jira-secret"));
    when(jiraConfig.projectKey()).thenReturn(Optional.of("OBS"));
    when(jiraConfig.issueType()).thenReturn("Bug");
    when(config.jira()).thenReturn(Optional.of(jiraConfig));

    NcdfeThrowingProducer producer = new NcdfeThrowingProducer();
    injectConfig(producer, config);

    Observarium result = producer.observarium(emptyInstance);

    assertThat(result.config().postingServiceCount())
        .as("Jira should succeed even though GitHub threw NCDFE")
        .isEqualTo(1);
  }

  // ---------------------------------------------------------------------------
  // Baseline: normal behaviour when modules are present
  // ---------------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void producer_createsGithubService_normally_whenModuleIsPresent() throws Exception {
    enableGitHubConfig();
    ObservariumProducer producer = new ObservariumProducer();
    injectConfig(producer, config);

    Observarium result = producer.observarium(emptyInstance);

    assertThat(result.config().postingServiceCount())
        .as("GitHub service must be created when module is on the classpath")
        .isEqualTo(1);

    List<ILoggingEvent> ncdfeWarnings =
        logAppender.list.stream()
            .filter(e -> e.getLevel() == Level.WARN)
            .filter(e -> e.getFormattedMessage().contains("observarium-github"))
            .toList();
    assertThat(ncdfeWarnings)
        .as("No NCDFE warning should be emitted when module is present")
        .isEmpty();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private void enableGitHubConfig() {
    when(githubConfig.enabled()).thenReturn(true);
    when(githubConfig.token()).thenReturn(Optional.of("ghp_token"));
    when(githubConfig.owner()).thenReturn(Optional.of("acme"));
    when(githubConfig.repo()).thenReturn(Optional.of("backend"));
    when(githubConfig.labelPrefix()).thenReturn("observarium");
    when(githubConfig.baseUrl()).thenReturn(Optional.empty());
    when(config.github()).thenReturn(Optional.of(githubConfig));
  }

  private static void injectConfig(ObservariumProducer target, ObservariumQuarkusConfig value)
      throws Exception {
    Field field = ObservariumProducer.class.getDeclaredField("config");
    field.setAccessible(true);
    field.set(target, value);
  }

  /**
   * Subclass that overrides the GitHub factory method to throw {@link NoClassDefFoundError},
   * simulating a missing {@code observarium-github} JAR. All other factory methods delegate to the
   * real production implementation.
   *
   * <p>This causes the production {@code createConfiguredPostingServices()} catch blocks to
   * execute, not test-only catch blocks.
   */
  static final class NcdfeThrowingProducer extends ObservariumProducer {

    @Override
    PostingService createGitHubService(ObservariumQuarkusConfig.GitHub gh) {
      throw new NoClassDefFoundError("io/hephaistos/observarium/github/GitHubPostingService");
    }
  }
}
