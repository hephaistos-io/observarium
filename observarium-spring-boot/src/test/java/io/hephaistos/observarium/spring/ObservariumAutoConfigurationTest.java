package io.hephaistos.observarium.spring;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.fingerprint.ExceptionFingerprinter;
import io.hephaistos.observarium.handler.ObservariumExceptionHandler;
import io.hephaistos.observarium.scrub.DataScrubber;
import io.hephaistos.observarium.scrub.ScrubLevel;
import io.hephaistos.observarium.trace.TraceContextProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ObservariumAutoConfiguration}.
 *
 * <p>Uses {@link ApplicationContextRunner} to exercise the auto-configuration in isolation
 * without starting a full Spring Boot application.
 */
class ObservariumAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ObservariumAutoConfiguration.class));

    @Test
    void observariumBeanIsCreatedWithDefaultProperties() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(Observarium.class);
            assertThat(context).hasSingleBean(ExceptionFingerprinter.class);
            assertThat(context).hasSingleBean(DataScrubber.class);
            assertThat(context).hasSingleBean(TraceContextProvider.class);
            assertThat(context).hasSingleBean(ObservariumExceptionHandler.class);
        });
    }

    @Test
    void observariumIsDisabledWhenPropertySetToFalse() {
        runner.withPropertyValues("observarium.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(Observarium.class);
                });
    }

    @Test
    void defaultScrubLevelIsBasic() {
        runner.run(context -> {
            ObservariumProperties properties = context.getBean(ObservariumProperties.class);
            assertThat(properties.getScrubLevel()).isEqualTo(ScrubLevel.BASIC);
        });
    }

    @Test
    void scrubLevelCanBeOverriddenViaProperties() {
        runner.withPropertyValues("observarium.scrub-level=STRICT")
                .run(context -> {
                    ObservariumProperties properties = context.getBean(ObservariumProperties.class);
                    assertThat(properties.getScrubLevel()).isEqualTo(ScrubLevel.STRICT);
                });
    }

    @Test
    void defaultMdcKeysAreConfigured() {
        runner.run(context -> {
            ObservariumProperties properties = context.getBean(ObservariumProperties.class);
            assertThat(properties.getTraceIdMdcKey()).isEqualTo("trace_id");
            assertThat(properties.getSpanIdMdcKey()).isEqualTo("span_id");
        });
    }

    @Test
    void mdcKeysCanBeOverriddenViaProperties() {
        runner.withPropertyValues(
                "observarium.trace-id-mdc-key=traceId",
                "observarium.span-id-mdc-key=spanId"
        ).run(context -> {
            ObservariumProperties properties = context.getBean(ObservariumProperties.class);
            assertThat(properties.getTraceIdMdcKey()).isEqualTo("traceId");
            assertThat(properties.getSpanIdMdcKey()).isEqualTo("spanId");
        });
    }

    @Test
    void userDefinedObservariumBeanTakesPrecedence() {
        Observarium customObservarium = Observarium.builder().build();
        runner.withBean(Observarium.class, () -> customObservarium)
                .run(context -> {
                    assertThat(context).hasSingleBean(Observarium.class);
                    assertThat(context.getBean(Observarium.class)).isSameAs(customObservarium);
                });
    }

    @Test
    void apiKeyIsPassedThroughFromProperties() {
        runner.withPropertyValues("observarium.api-key=test-api-key-123")
                .run(context -> {
                    ObservariumProperties properties = context.getBean(ObservariumProperties.class);
                    assertThat(properties.getApiKey()).isEqualTo("test-api-key-123");
                });
    }

    @Test
    void githubIsDisabledByDefault() {
        runner.run(context -> {
            ObservariumProperties properties = context.getBean(ObservariumProperties.class);
            assertThat(properties.getGithub().isEnabled()).isFalse();
        });
    }

    @Test
    void jiraIsDisabledByDefault() {
        runner.run(context -> {
            ObservariumProperties properties = context.getBean(ObservariumProperties.class);
            assertThat(properties.getJira().isEnabled()).isFalse();
        });
    }

    @Test
    void gitlabIsDisabledByDefault() {
        runner.run(context -> {
            ObservariumProperties properties = context.getBean(ObservariumProperties.class);
            assertThat(properties.getGitlab().isEnabled()).isFalse();
        });
    }

    @Test
    void emailIsDisabledByDefault() {
        runner.run(context -> {
            ObservariumProperties properties = context.getBean(ObservariumProperties.class);
            assertThat(properties.getEmail().isEnabled()).isFalse();
        });
    }
}
