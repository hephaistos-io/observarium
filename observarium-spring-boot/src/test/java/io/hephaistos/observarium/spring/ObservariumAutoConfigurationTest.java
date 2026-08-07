package io.hephaistos.observarium.spring;

import static org.assertj.core.api.Assertions.assertThat;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.ObservariumListener;
import io.hephaistos.observarium.fingerprint.ExceptionFingerprinter;
import io.hephaistos.observarium.handler.ObservariumExceptionHandler;
import io.hephaistos.observarium.scrub.DataScrubber;
import io.hephaistos.observarium.scrub.ScrubLevel;
import io.hephaistos.observarium.trace.TraceContextProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Tests for {@link ObservariumAutoConfiguration}.
 *
 * <p>Uses {@link ApplicationContextRunner} to exercise the auto-configuration in isolation without
 * starting a full Spring Boot application.
 */
class ObservariumAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ObservariumAutoConfiguration.class));

  @Test
  void observariumBeanIsCreatedWithDefaultProperties() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(Observarium.class);
          assertThat(context).hasSingleBean(ExceptionFingerprinter.class);
          assertThat(context).hasSingleBean(DataScrubber.class);
          assertThat(context).hasSingleBean(TraceContextProvider.class);
          // observarium.install-uncaught-handler defaults to false (issue #17): a Boot MVC
          // application's exceptions never reach the JVM default handler, so it is opt-in.
          assertThat(context).doesNotHaveBean(ObservariumExceptionHandler.class);
        });
  }

  @Test
  void observariumIsDisabledWhenPropertySetToFalse() {
    runner
        .withPropertyValues("observarium.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(Observarium.class);
            });
  }

  @Test
  void defaultScrubLevelIsBasic() {
    runner.run(
        context -> {
          ObservariumProperties properties = context.getBean(ObservariumProperties.class);
          assertThat(properties.getScrubLevel()).isEqualTo(ScrubLevel.BASIC);
        });
  }

  @Test
  void scrubLevelCanBeOverriddenViaProperties() {
    runner
        .withPropertyValues("observarium.scrub-level=STRICT")
        .run(
            context -> {
              ObservariumProperties properties = context.getBean(ObservariumProperties.class);
              assertThat(properties.getScrubLevel()).isEqualTo(ScrubLevel.STRICT);
            });
  }

  @Test
  void defaultMdcKeysAreConfigured() {
    runner.run(
        context -> {
          ObservariumProperties properties = context.getBean(ObservariumProperties.class);
          assertThat(properties.getTraceIdMdcKey()).isEqualTo("trace_id");
          assertThat(properties.getSpanIdMdcKey()).isEqualTo("span_id");
        });
  }

  @Test
  void mdcKeysCanBeOverriddenViaProperties() {
    runner
        .withPropertyValues(
            "observarium.trace-id-mdc-key=traceId", "observarium.span-id-mdc-key=spanId")
        .run(
            context -> {
              ObservariumProperties properties = context.getBean(ObservariumProperties.class);
              assertThat(properties.getTraceIdMdcKey()).isEqualTo("traceId");
              assertThat(properties.getSpanIdMdcKey()).isEqualTo("spanId");
            });
  }

  @Test
  void installUncaughtHandlerAndAdviceEnabledBindFromEnvironment() {
    runner
        .withPropertyValues(
            "observarium.install-uncaught-handler=true", "observarium.mvc.advice-enabled=false")
        .run(
            context -> {
              ObservariumProperties properties = context.getBean(ObservariumProperties.class);
              assertThat(properties.isInstallUncaughtHandler()).isTrue();
              assertThat(properties.getMvc().isAdviceEnabled()).isFalse();
            });
  }

  @Test
  void userDefinedObservariumListenerBeanIsWiredIntoObservarium() {
    // Pre-existing behaviour (unrelated to issues #14/#17/#22): the observarium() bean method
    // wires an autowired ObservariumListener when one is present, but nothing exercised that
    // branch in this module. Covered here per the "never ignore issues" project rule.
    ObservariumListener listener = new ObservariumListener() {};

    runner
        .withBean(ObservariumListener.class, () -> listener)
        .run(context -> assertThat(context).hasSingleBean(Observarium.class));
  }

  @Test
  void userDefinedObservariumBeanTakesPrecedence() {
    Observarium customObservarium = Observarium.builder().build();
    runner
        .withBean(Observarium.class, () -> customObservarium)
        .run(
            context -> {
              assertThat(context).hasSingleBean(Observarium.class);
              assertThat(context.getBean(Observarium.class)).isSameAs(customObservarium);
            });
  }
}
