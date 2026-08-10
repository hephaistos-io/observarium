package io.hephaistos.observarium.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.hephaistos.observarium.ObservariumListener;
import io.hephaistos.observarium.micrometer.ObservariumMeterBinder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Tests for the Micrometer slice of {@link ObservariumAutoConfiguration}, active because {@code
 * observarium-micrometer} and {@code micrometer-core} are on the test classpath.
 */
class MicrometerAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ObservariumAutoConfiguration.class));

  @Test
  void meterBinderIsRegisteredAsTheObservariumListener() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(ObservariumMeterBinder.class);
          assertThat(context.getBean(ObservariumListener.class))
              .isInstanceOf(ObservariumMeterBinder.class);
        });
  }

  @Test
  void applicationListenerBeanTakesPrecedenceOverMeterBinder() {
    ObservariumListener custom = new ObservariumListener() {};
    runner
        .withBean(ObservariumListener.class, () -> custom)
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(ObservariumMeterBinder.class);
              assertThat(context.getBean(ObservariumListener.class)).isSameAs(custom);
            });
  }
}
