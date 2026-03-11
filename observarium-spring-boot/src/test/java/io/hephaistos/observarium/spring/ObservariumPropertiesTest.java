package io.hephaistos.observarium.spring;

import static org.assertj.core.api.Assertions.assertThat;

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
