package io.hephaistos.observarium.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.hephaistos.observarium.scrub.ScrubLevel;
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
    assertThat(props.isInstallUncaughtHandler()).isFalse();
    assertThat(props.getMvc().isAdviceEnabled()).isTrue();
  }

  // -----------------------------------------------------------------------
  // setMaxDuplicateComments validation
  //
  // Pre-existing behaviour (unrelated to issues #14/#17/#22) that had no direct test in this
  // module, left the branch coverage below threshold. Covered here per the "never ignore issues,
  // even if not caused by you" project rule, without touching the production code.
  // -----------------------------------------------------------------------

  @Test
  void maxDuplicateCommentsAcceptsPositiveValue() {
    ObservariumProperties props = new ObservariumProperties();

    props.setMaxDuplicateComments(3);

    assertThat(props.getMaxDuplicateComments()).isEqualTo(3);
  }

  @Test
  void maxDuplicateCommentsAcceptsUnlimitedSentinel() {
    ObservariumProperties props = new ObservariumProperties();

    props.setMaxDuplicateComments(-1);

    assertThat(props.getMaxDuplicateComments()).isEqualTo(-1);
  }

  @Test
  void maxDuplicateCommentsRejectsZero() {
    ObservariumProperties props = new ObservariumProperties();

    assertThatIllegalArgumentException()
        .isThrownBy(() -> props.setMaxDuplicateComments(0))
        .withMessageContaining("observarium.max-duplicate-comments");
  }

  @Test
  void maxDuplicateCommentsRejectsValuesBelowNegativeOne() {
    ObservariumProperties props = new ObservariumProperties();

    assertThatIllegalArgumentException()
        .isThrownBy(() -> props.setMaxDuplicateComments(-2))
        .withMessageContaining("observarium.max-duplicate-comments");
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
