package io.hephaistos.observarium.demo.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.event.Severity;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DemoApplicationTest {

  @Inject Observarium observarium;
  @Inject MeterRegistry meterRegistry;

  @Test
  void contextLoads() {
    assertThat(observarium).isNotNull();
  }

  @Test
  void twoPostingServicesDiscoveredViaSpi() {
    assertThat(observarium.config().postingServiceCount()).isEqualTo(2);
  }

  @Test
  void capturedCounterIncrementsOnCapture() {
    observarium.captureException(new RuntimeException("test"), Severity.WARNING);

    assertThat(
            meterRegistry
                .find("observarium.exceptions.captured")
                .tag("severity", "warning")
                .counter())
        .isNotNull()
        .satisfies(counter -> assertThat(counter.count()).isGreaterThanOrEqualTo(1.0));
  }

  @Test
  void queueSizeGaugeRegistered() {
    assertThat(meterRegistry.find("observarium.queue.size").gauge()).isNotNull();
  }
}
