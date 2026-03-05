package io.hephaistos.observarium.spring;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.fingerprint.DefaultExceptionFingerprinter;
import io.hephaistos.observarium.fingerprint.ExceptionFingerprinter;
import io.hephaistos.observarium.handler.ObservariumExceptionHandler;
import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.scrub.DataScrubber;
import io.hephaistos.observarium.scrub.DefaultDataScrubber;
import io.hephaistos.observarium.trace.MdcTraceContextProvider;
import io.hephaistos.observarium.trace.TraceContextProvider;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for the Observarium exception tracking library.
 *
 * <p>Activated when {@code observarium.enabled=true} (or when the property is absent, since {@code
 * matchIfMissing=true}). All beans are marked {@link ConditionalOnMissingBean} so that applications
 * can override any component by declaring their own bean of the same type.
 */
@AutoConfiguration
@EnableConfigurationProperties(ObservariumProperties.class)
@ConditionalOnProperty(name = "observarium.enabled", havingValue = "true", matchIfMissing = true)
public class ObservariumAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ExceptionFingerprinter exceptionFingerprinter() {
    return new DefaultExceptionFingerprinter();
  }

  @Bean
  @ConditionalOnMissingBean
  public TraceContextProvider traceContextProvider(ObservariumProperties properties) {
    return new MdcTraceContextProvider(properties.getTraceIdMdcKey(), properties.getSpanIdMdcKey());
  }

  @Bean
  @ConditionalOnMissingBean
  public DataScrubber dataScrubber(ObservariumProperties properties) {
    return new DefaultDataScrubber(properties.getScrubLevel());
  }

  /**
   * Creates the central {@link Observarium} instance, injecting all {@link PostingService} beans
   * found in the application context (including those registered by the posting service
   * auto-configurations in this module).
   */
  @Bean
  @ConditionalOnMissingBean
  public Observarium observarium(
      ObservariumProperties properties,
      ExceptionFingerprinter fingerprinter,
      DataScrubber scrubber,
      TraceContextProvider traceContextProvider,
      @Autowired(required = false) List<PostingService> postingServices) {

    var builder =
        Observarium.builder()
            .scrubLevel(properties.getScrubLevel())
            .fingerprinter(fingerprinter)
            .scrubber(scrubber)
            .traceContextProvider(traceContextProvider);

    if (postingServices != null) {
      postingServices.forEach(builder::addPostingService);
    }

    return builder.build();
  }

  /**
   * Creates and installs the Observarium handler as the JVM default uncaught exception handler,
   * preserving any existing handler as a delegate.
   */
  @Bean
  @ConditionalOnMissingBean
  public ObservariumExceptionHandler observariumExceptionHandler(Observarium observarium) {
    Thread.UncaughtExceptionHandler existing = Thread.getDefaultUncaughtExceptionHandler();
    var handler = new ObservariumExceptionHandler(observarium, existing);
    Thread.setDefaultUncaughtExceptionHandler(handler);
    return handler;
  }

  /**
   * Registers the Spring MVC global exception handler as a bean when DispatcherServlet is on the
   * classpath. The {@link ObservariumGlobalExceptionHandler} is a {@link
   * org.springframework.web.bind.annotation.ControllerAdvice} so it must be registered as a bean to
   * be picked up by Spring MVC, not listed in the auto-configuration imports file.
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
  public ObservariumGlobalExceptionHandler observariumGlobalExceptionHandler(
      Observarium observarium) {
    return new ObservariumGlobalExceptionHandler(observarium);
  }
}
