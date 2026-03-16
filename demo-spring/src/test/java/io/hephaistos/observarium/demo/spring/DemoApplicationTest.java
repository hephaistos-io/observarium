package io.hephaistos.observarium.demo.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.event.Severity;
import io.hephaistos.observarium.micrometer.ObservariumMeterBinder;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(
    properties = {
      "observarium.github.enabled=true",
      "observarium.github.token=ghp_test",
      "observarium.github.owner=acme",
      "observarium.github.repo=backend",
      "observarium.gitlab.enabled=true",
      "observarium.gitlab.base-url=https://gitlab.com",
      "observarium.gitlab.private-token=glpat-test",
      "observarium.gitlab.project-id=42"
    })
class DemoApplicationTest {

  @Autowired private Observarium observarium;
  @Autowired private MeterRegistry meterRegistry;
  @Autowired private ObservariumMeterBinder meterBinder;

  @Test
  void contextLoads() {
    assertThat(observarium).isNotNull();
  }

  @Test
  void twoPostingServicesDiscoveredViaSpi() {
    assertThat(observarium.config().postingServiceCount()).isEqualTo(2);
  }

  @Test
  void meterBinderAutoConfigured() {
    assertThat(meterBinder).isNotNull();
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
