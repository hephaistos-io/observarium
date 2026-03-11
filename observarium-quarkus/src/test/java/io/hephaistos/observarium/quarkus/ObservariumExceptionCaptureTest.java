package io.hephaistos.observarium.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.hephaistos.observarium.Observarium;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * CDI integration test for {@link ObservariumExceptionMapper}.
 *
 * <p>Boots the ArC CDI container via {@link QuarkusComponentTest} to verify that the mapper is
 * discoverable as a CDI bean and that its {@link Observarium} dependency is correctly injected.
 * This complements the unit-level tests in {@link ObservariumExceptionMapperTest} by exercising
 * real CDI wiring rather than reflective field injection.
 *
 * <p>Note: A full HTTP-container test using {@code @QuarkusTest} is not feasible with Quarkus
 * 3.17.x and Gradle 9.x due to a known incompatibility in the Quarkus Gradle plugin (fixed in
 * Quarkus 3.25+).
 */
@QuarkusComponentTest
@TestConfigProperty(key = "observarium.enabled", value = "true")
class ObservariumExceptionCaptureTest {

  @Inject ObservariumExceptionMapper exceptionMapper;

  /** Replaces the {@link Observarium} CDI bean with a Mockito mock for this test class. */
  @InjectMock Observarium observarium;

  @Test
  void mapper_isDiscoverableAsCdiBean() {
    assertThat(exceptionMapper)
        .as("CDI container must discover and inject ObservariumExceptionMapper")
        .isNotNull();
  }

  @Test
  void mapper_receivesInjectedObservarium_andDelegatesCapture() {
    // This is the key CDI test: the Observarium dependency was injected by ArC
    // (as a mock via @InjectMock), proving the @Inject wiring works.
    RuntimeException cause = new RuntimeException("CDI integration test");

    exceptionMapper.toResponse(cause);

    verify(observarium).captureException(cause);
  }
}
