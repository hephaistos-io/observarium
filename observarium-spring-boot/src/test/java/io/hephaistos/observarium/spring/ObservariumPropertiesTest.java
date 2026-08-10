package io.hephaistos.observarium.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.scrub.ScrubLevel;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ObservariumProperties}.
 *
 * <p>Plain POJO tests — no Spring context is required.
 */
class ObservariumPropertiesTest {

  @Test
  void defaultValuesAreCorrect() {
    ObservariumProperties props = new ObservariumProperties();

    assertThat(props.isEnabled()).isTrue();
    assertThat(props.getScrubLevel()).isEqualTo(ScrubLevel.BASIC);
    assertThat(props.getTraceIdMdcKey()).isEqualTo("trace_id");
    assertThat(props.getSpanIdMdcKey()).isEqualTo("span_id");
    assertThat(props.getMaxDuplicateComments()).isEqualTo(5);
    assertThat(props.getQueueCapacity()).isEqualTo(256);
    assertThat(props.getScrubPatterns()).isEmpty();
    assertThat(props.getCompiledScrubPatterns()).isEmpty();
    assertThat(props.getIgnoredExceptions()).isEmpty();
    assertThat(props.getShutdownTimeout()).isEqualTo(Observarium.DEFAULT_SHUTDOWN_TIMEOUT);
    assertThat(props.isInstallUncaughtHandler()).isFalse();
    assertThat(props.getMvc().isAdviceEnabled()).isTrue();
  }

  @Test
  void queueCapacitySetterRoundTrips() {
    ObservariumProperties props = new ObservariumProperties();
    props.setQueueCapacity(1024);
    assertThat(props.getQueueCapacity()).isEqualTo(1024);
  }

  @Test
  void queueCapacityRejectsZeroAndNegative() {
    ObservariumProperties props = new ObservariumProperties();
    assertThatThrownBy(() -> props.setQueueCapacity(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> props.setQueueCapacity(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void scrubPatternsAreCompiledEagerly() {
    ObservariumProperties props = new ObservariumProperties();
    props.setScrubPatterns(List.of("ORD-\\d+", "[0-9a-f]{8}"));

    assertThat(props.getScrubPatterns()).containsExactly("ORD-\\d+", "[0-9a-f]{8}");
    assertThat(props.getCompiledScrubPatterns()).hasSize(2);
    assertThat(props.getCompiledScrubPatterns().get(0).pattern()).isEqualTo("ORD-\\d+");
  }

  @Test
  void invalidScrubPattern_failsFastNamingThePattern() {
    ObservariumProperties props = new ObservariumProperties();
    assertThatThrownBy(() -> props.setScrubPatterns(List.of("valid-\\d+", "[unterminated")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("[unterminated");
  }

  @Test
  void ignoredExceptionsSetterRoundTrips() {
    ObservariumProperties props = new ObservariumProperties();
    props.setIgnoredExceptions(List.of("com.example.NotFoundException"));
    assertThat(props.getIgnoredExceptions()).containsExactly("com.example.NotFoundException");
  }

  @Test
  void nullScrubPatterns_isTreatedAsEmpty() {
    ObservariumProperties props = new ObservariumProperties();
    props.setScrubPatterns(null);
    assertThat(props.getScrubPatterns()).isEmpty();
    assertThat(props.getCompiledScrubPatterns()).isEmpty();
  }

  @Test
  void nullIgnoredExceptions_isTreatedAsEmpty() {
    ObservariumProperties props = new ObservariumProperties();
    props.setIgnoredExceptions(null);
    assertThat(props.getIgnoredExceptions()).isEmpty();
  }

  @Test
  void maxDuplicateCommentsSetterRoundTrips() {
    ObservariumProperties props = new ObservariumProperties();
    props.setMaxDuplicateComments(10);
    assertThat(props.getMaxDuplicateComments()).isEqualTo(10);
  }

  @Test
  void maxDuplicateCommentsRejectsZeroAndBelowMinusOne() {
    ObservariumProperties props = new ObservariumProperties();
    assertThatThrownBy(() -> props.setMaxDuplicateComments(0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> props.setMaxDuplicateComments(-2))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void maxDuplicateCommentsAcceptsMinusOne() {
    ObservariumProperties props = new ObservariumProperties();
    props.setMaxDuplicateComments(-1);
    assertThat(props.getMaxDuplicateComments()).isEqualTo(-1);
  }

  @Test
  void shutdownTimeoutSetterRoundTrips() {
    ObservariumProperties props = new ObservariumProperties();
    props.setShutdownTimeout(Duration.ofSeconds(30));
    assertThat(props.getShutdownTimeout()).isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void shutdownTimeoutRejectsZeroNegativeAndNull() {
    ObservariumProperties props = new ObservariumProperties();
    assertThatThrownBy(() -> props.setShutdownTimeout(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> props.setShutdownTimeout(Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> props.setShutdownTimeout(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void installUncaughtHandlerAndAdviceEnabledRoundTrip() {
    ObservariumProperties props = new ObservariumProperties();

    props.setInstallUncaughtHandler(true);
    assertThat(props.isInstallUncaughtHandler()).isTrue();

    props.getMvc().setAdviceEnabled(false);
    assertThat(props.getMvc().isAdviceEnabled()).isFalse();
  }

  @Test
  void topLevelSettersRoundTrip() {
    ObservariumProperties props = new ObservariumProperties();

    props.setEnabled(false);
    assertThat(props.isEnabled()).isFalse();

    props.setScrubLevel(ScrubLevel.STRICT);
    assertThat(props.getScrubLevel()).isEqualTo(ScrubLevel.STRICT);

    props.setTraceIdMdcKey("X-Trace-Id");
    assertThat(props.getTraceIdMdcKey()).isEqualTo("X-Trace-Id");

    props.setSpanIdMdcKey("X-Span-Id");
    assertThat(props.getSpanIdMdcKey()).isEqualTo("X-Span-Id");
  }
}
