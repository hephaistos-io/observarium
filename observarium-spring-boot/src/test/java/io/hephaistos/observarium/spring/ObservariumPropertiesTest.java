package io.hephaistos.observarium.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    assertThat(props.getMaxDuplicateComments()).isEqualTo(5);
  }

  @Test
  void maxDuplicateCommentsAcceptsPositiveValues() {
    ObservariumProperties props = new ObservariumProperties();

    props.setMaxDuplicateComments(12);

    assertThat(props.getMaxDuplicateComments()).isEqualTo(12);
  }

  @Test
  void maxDuplicateCommentsAcceptsMinusOneForUnlimited() {
    ObservariumProperties props = new ObservariumProperties();

    props.setMaxDuplicateComments(-1);

    assertThat(props.getMaxDuplicateComments()).isEqualTo(-1);
  }

  @Test
  void maxDuplicateCommentsRejectsZero() {
    ObservariumProperties props = new ObservariumProperties();

    assertThatThrownBy(() -> props.setMaxDuplicateComments(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("observarium.max-duplicate-comments");
  }

  @Test
  void maxDuplicateCommentsRejectsValuesBelowMinusOne() {
    ObservariumProperties props = new ObservariumProperties();

    assertThatThrownBy(() -> props.setMaxDuplicateComments(-2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("-2");
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
