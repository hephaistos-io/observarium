package io.hephaistos.observarium.demo.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import io.hephaistos.observarium.Observarium;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DemoApplicationTest {

  @Inject Observarium observarium;

  @Test
  void contextLoads() {
    assertThat(observarium).isNotNull();
  }

  @Test
  void twoPostingServicesDiscoveredViaSpi() {
    assertThat(observarium.config().postingServiceCount()).isEqualTo(2);
  }
}
