package io.hephaistos.observarium.posting;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class PostingServiceDiagnosticsTest {

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
    return (Logger) LoggerFactory.getLogger(PostingServiceDiagnosticsTest.class);
  }

  @Test
  void warns_whenAllRequiredKeysPresentAndNotEnabled() {
    PostingServiceDiagnostics.warnIfConfiguredButNotEnabled(
        logger(),
        "github",
        Map.of("token", "t", "owner", "o", "repo", "r"),
        List.of("token", "owner", "repo"));

    assertThat(appender.list)
        .extracting(ILoggingEvent::getFormattedMessage)
        .anySatisfy(
            message ->
                assertThat(message)
                    .contains("github")
                    .contains("token")
                    .contains("owner")
                    .contains("repo")
                    .contains("not enabled")
                    .contains("github.enabled=true"));
  }

  @Test
  void doesNotWarn_whenNoConfigPresent() {
    PostingServiceDiagnostics.warnIfConfiguredButNotEnabled(
        logger(), "github", Map.of(), List.of("token", "owner", "repo"));
    assertThat(appender.list).isEmpty();
  }

  @Test
  void doesNotWarn_whenOnlySomeRequiredKeysPresent() {
    PostingServiceDiagnostics.warnIfConfiguredButNotEnabled(
        logger(), "github", Map.of("token", "t"), List.of("token", "owner", "repo"));
    assertThat(appender.list).isEmpty();
  }

  @Test
  void doesNotWarn_whenBlankValueCountsAsMissing() {
    PostingServiceDiagnostics.warnIfConfiguredButNotEnabled(
        logger(),
        "github",
        Map.of("token", "t", "owner", "  ", "repo", "r"),
        List.of("token", "owner", "repo"));
    assertThat(appender.list).isEmpty();
  }

  @Test
  void doesNotWarn_whenEnabledIsTrue() {
    PostingServiceDiagnostics.warnIfConfiguredButNotEnabled(
        logger(),
        "github",
        Map.of("enabled", "true", "token", "t", "owner", "o", "repo", "r"),
        List.of("token", "owner", "repo"));
    assertThat(appender.list).isEmpty();
  }

  @Test
  void doesNotWarn_whenEnabledIsTrueCaseInsensitive() {
    PostingServiceDiagnostics.warnIfConfiguredButNotEnabled(
        logger(),
        "github",
        Map.of("enabled", "TRUE", "token", "t", "owner", "o", "repo", "r"),
        List.of("token", "owner", "repo"));
    assertThat(appender.list).isEmpty();
  }

  @Test
  void doesNotWarn_whenRequiredKeysListIsEmpty() {
    PostingServiceDiagnostics.warnIfConfiguredButNotEnabled(
        logger(), "custom", Map.of("anything", "value"), List.of());
    assertThat(appender.list).isEmpty();
  }
}
