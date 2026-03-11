package io.hephaistos.observarium.demo.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.hephaistos.observarium.Observarium;
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

  @Test
  void contextLoads() {
    assertThat(observarium).isNotNull();
  }

  @Test
  void twoPostingServicesDiscoveredViaSpi() {
    assertThat(observarium.config().postingServiceCount()).isEqualTo(2);
  }
}
