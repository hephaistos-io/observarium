package io.hephaistos.observarium.spring;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.ObservariumListener;
import io.hephaistos.observarium.fingerprint.DefaultExceptionFingerprinter;
import io.hephaistos.observarium.fingerprint.ExceptionFingerprinter;
import io.hephaistos.observarium.handler.ObservariumExceptionHandler;
import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.posting.PostingServiceFactory;
import io.hephaistos.observarium.scrub.DataScrubber;
import io.hephaistos.observarium.scrub.DefaultDataScrubber;
import io.hephaistos.observarium.trace.MdcTraceContextProvider;
import io.hephaistos.observarium.trace.TraceContextProvider;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.ControllerAdvice;

/**
 * Spring Boot auto-configuration for the Observarium exception tracking library.
 *
 * <p>Activated when {@code observarium.enabled=true} (or when the property is absent, since {@code
 * matchIfMissing=true}). All beans are marked {@link ConditionalOnMissingBean} so that applications
 * can override any component by declaring their own bean of the same type.
 *
 * <p>Posting services are discovered via {@link ServiceLoader} from the {@link
 * PostingServiceFactory} SPI. Each posting module on the classpath registers its factory in {@code
 * META-INF/services}, so no explicit dependency wiring is needed here.
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
   * Creates the central {@link Observarium} instance.
   *
   * <p>Posting services come from two sources:
   *
   * <ol>
   *   <li>User-registered {@link PostingService} beans in the application context.
   *   <li>{@link PostingServiceFactory} implementations discovered via {@link ServiceLoader},
   *       configured from the Spring {@link Environment}.
   * </ol>
   */
  @Bean(destroyMethod = "shutdown")
  @ConditionalOnMissingBean
  public Observarium observarium(
      ObservariumProperties properties,
      ExceptionFingerprinter fingerprinter,
      DataScrubber scrubber,
      TraceContextProvider traceContextProvider,
      Environment environment,
      @Autowired(required = false) List<PostingService> postingServices,
      @Autowired(required = false) ObservariumListener listener) {

    var builder =
        Observarium.builder()
            .scrubLevel(properties.getScrubLevel())
            .fingerprinter(fingerprinter)
            .scrubber(scrubber)
            .traceContextProvider(traceContextProvider)
            .maxDuplicateComments(properties.getMaxDuplicateComments());

    if (listener != null) {
      builder.listener(listener);
    }

    if (postingServices != null) {
      postingServices.forEach(builder::addPostingService);
    }

    Binder binder = Binder.get(environment);
    for (PostingServiceFactory factory : ServiceLoader.load(PostingServiceFactory.class)) {
      String prefix = "observarium." + factory.id();
      Map<String, String> props =
          binder.bind(prefix, Bindable.mapOf(String.class, String.class)).orElse(Map.of());
      factory.create(props).ifPresent(builder::addPostingService);
    }

    return builder.build();
  }

  /**
   * Creates and installs the Observarium handler as the JVM default uncaught exception handler,
   * preserving any existing handler as a delegate.
   *
   * <p>Disabled by default ({@code observarium.install-uncaught-handler=false}) because a Boot MVC
   * application's own threads have their exceptions caught by the servlet container long before
   * they would reach the JVM default handler — installing it buys little there while mutating
   * global JVM state. Opt in via {@code observarium.install-uncaught-handler=true} for applications
   * that spawn their own unmanaged threads. The bean's {@code destroyMethod} restores the previous
   * default handler on context close, guarded so it only restores if this handler is still the
   * current default — see {@link ObservariumExceptionHandler#uninstall()}.
   */
  @Bean(destroyMethod = "uninstall")
  @ConditionalOnMissingBean
  @ConditionalOnProperty(name = "observarium.install-uncaught-handler", havingValue = "true")
  public ObservariumExceptionHandler observariumExceptionHandler(Observarium observarium) {
    Thread.UncaughtExceptionHandler existing = Thread.getDefaultUncaughtExceptionHandler();
    var handler = new ObservariumExceptionHandler(observarium, existing);
    Thread.setDefaultUncaughtExceptionHandler(handler);
    return handler;
  }

  /**
   * Registers the Spring MVC global exception handler as a bean when DispatcherServlet is on the
   * classpath, unless the application already defines its own {@code @ControllerAdvice} — see
   * {@link ObservariumGlobalExceptionHandler} for why an application-owned advice always wins over
   * this one, and why this bean is skipped entirely instead of being registered inert. Explicit
   * opt-out is available via {@code observarium.mvc.advice-enabled=false} regardless of whether an
   * application advice is present.
   */
  @Bean
  @ConditionalOnMissingBean(annotation = ControllerAdvice.class)
  @ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
  @ConditionalOnProperty(
      name = "observarium.mvc.advice-enabled",
      havingValue = "true",
      matchIfMissing = true)
  public ObservariumGlobalExceptionHandler observariumGlobalExceptionHandler(
      Observarium observarium) {
    return new ObservariumGlobalExceptionHandler(observarium);
  }

  /**
   * Isolated configuration class that is only loaded when both {@code observarium-micrometer} and
   * Micrometer are on the classpath. The nested class ensures the JVM never attempts to resolve
   * {@link io.hephaistos.observarium.micrometer.ObservariumMeterBinder} when the dependency is
   * absent, avoiding {@link NoClassDefFoundError}.
   */
  @Configuration
  @ConditionalOnClass(
      name = {
        "io.micrometer.core.instrument.MeterRegistry",
        "io.hephaistos.observarium.micrometer.ObservariumMeterBinder"
      })
  static class MicrometerConfiguration {

    /**
     * Registers the Micrometer meter binder as the {@link ObservariumListener} when both {@code
     * observarium-micrometer} and a {@link io.micrometer.core.instrument.MeterRegistry} are on the
     * classpath. The binder also implements {@link
     * io.micrometer.core.instrument.binder.MeterBinder}, so Spring Boot Actuator will auto-discover
     * it and call {@code bindTo()} to register the meters.
     */
    @Bean
    @ConditionalOnMissingBean(ObservariumListener.class)
    public io.hephaistos.observarium.micrometer.ObservariumMeterBinder observariumMeterBinder() {
      return new io.hephaistos.observarium.micrometer.ObservariumMeterBinder();
    }
  }
}
