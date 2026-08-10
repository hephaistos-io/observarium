package io.hephaistos.observarium.quarkus;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.ObservariumListener;
import io.hephaistos.observarium.filter.IgnoredExceptionMatcher;
import io.hephaistos.observarium.fingerprint.DefaultExceptionFingerprinter;
import io.hephaistos.observarium.handler.ObservariumExceptionHandler;
import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.posting.PostingServiceFactory;
import io.hephaistos.observarium.scrub.DefaultDataScrubber;
import io.hephaistos.observarium.scrub.ScrubLevel;
import io.hephaistos.observarium.trace.MdcTraceContextProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.eclipse.microprofile.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CDI producer that creates the central {@link Observarium} bean for Quarkus applications.
 *
 * <p>Posting services are discovered from two sources:
 *
 * <ol>
 *   <li>CDI-managed {@link PostingService} beans registered by the application.
 *   <li>{@link PostingServiceFactory} implementations discovered via {@link ServiceLoader},
 *       configured from MicroProfile Config.
 * </ol>
 */
@ApplicationScoped
public class ObservariumProducer {

  private static final Logger log = LoggerFactory.getLogger(ObservariumProducer.class);

  @Inject ObservariumQuarkusConfig config;

  @Inject Config mpConfig;

  private ObservariumExceptionHandler uncaughtHandler;

  @Produces
  @ApplicationScoped
  public Observarium observarium(
      Instance<PostingService> discoveredServices, Instance<ObservariumListener> listenerInstance) {
    if (!config.enabled()) {
      log.info("Observarium is disabled via configuration");
      return Observarium.builder().build();
    }

    ScrubLevel scrubLevel;
    try {
      scrubLevel = ScrubLevel.valueOf(config.scrubLevel());
    } catch (IllegalArgumentException e) {
      log.warn(
          "Invalid scrub level '{}' in configuration; falling back to BASIC", config.scrubLevel());
      scrubLevel = ScrubLevel.BASIC;
    }

    int maxDuplicateComments = config.maxDuplicateComments();
    if (maxDuplicateComments < -1 || maxDuplicateComments == 0) {
      log.warn(
          "Invalid observarium.max-duplicate-comments value '{}'; falling back to default 5",
          maxDuplicateComments);
      maxDuplicateComments = 5;
    }

    int queueCapacity = config.queueCapacity();
    if (queueCapacity <= 0) {
      throw new IllegalArgumentException(
          "observarium.queue-capacity must be greater than zero, got: " + queueCapacity);
    }

    // Passed into DefaultDataScrubber directly: Builder#addScrubPattern is inert once a custom
    // scrubber is supplied via Builder#scrubber, which this producer always does.
    List<Pattern> compiledScrubPatterns =
        compileScrubPatterns(config.scrubPatterns().orElse(List.of()));

    var builder =
        Observarium.builder()
            .scrubLevel(scrubLevel)
            .fingerprinter(new DefaultExceptionFingerprinter())
            .scrubber(new DefaultDataScrubber(scrubLevel, compiledScrubPatterns))
            .traceContextProvider(
                new MdcTraceContextProvider(config.traceIdMdcKey(), config.spanIdMdcKey()))
            .maxDuplicateComments(maxDuplicateComments)
            .queueCapacity(queueCapacity)
            .shutdownTimeout(config.shutdownTimeout());

    builder.ignoreIf(
        IgnoredExceptionMatcher.byFullyQualifiedNames(
            config.ignoredExceptions().orElse(List.of())));

    if (listenerInstance.isResolvable()) {
      builder.listener(listenerInstance.get());
    }

    // Add any PostingService beans discovered via CDI
    for (PostingService service : discoveredServices) {
      builder.addPostingService(service);
    }

    // Discover posting services via SPI factories
    for (PostingServiceFactory factory : ServiceLoader.load(PostingServiceFactory.class)) {
      Map<String, String> props = extractConfigMap("observarium." + factory.id() + ".");
      factory.create(props).ifPresent(builder::addPostingService);
    }

    var built = builder.build();
    if (config.installUncaughtHandler()) {
      uncaughtHandler =
          new ObservariumExceptionHandler(built, Thread.getDefaultUncaughtExceptionHandler());
      Thread.setDefaultUncaughtExceptionHandler(uncaughtHandler);
      log.info("Observarium installed as the JVM default uncaught exception handler");
    }
    return built;
  }

  /**
   * Shuts down the {@link Observarium} instance when the CDI context is destroyed, restoring the
   * previous default uncaught exception handler if this producer installed one.
   */
  public void dispose(@Disposes Observarium observarium) {
    if (uncaughtHandler != null) {
      uncaughtHandler.uninstall();
      uncaughtHandler = null;
    }
    observarium.shutdown();
  }

  /**
   * Compiles each configured regex eagerly, so an invalid entry in {@code
   * observarium.scrub-patterns} fails at startup with the offending pattern named.
   */
  private static List<Pattern> compileScrubPatterns(List<String> patterns) {
    List<Pattern> compiled = new ArrayList<>(patterns.size());
    for (String pattern : patterns) {
      try {
        compiled.add(Pattern.compile(pattern));
      } catch (PatternSyntaxException e) {
        throw new IllegalArgumentException(
            "observarium.scrub-patterns contains an invalid regex '"
                + pattern
                + "': "
                + e.getMessage(),
            e);
      }
    }
    return compiled;
  }

  /**
   * Extracts all config properties under the given prefix into a flat map with prefix-stripped,
   * kebab-case keys.
   */
  Map<String, String> extractConfigMap(String prefix) {
    Map<String, String> result = new HashMap<>();
    for (String name : mpConfig.getPropertyNames()) {
      if (name.startsWith(prefix)) {
        String key = name.substring(prefix.length());
        mpConfig.getOptionalValue(name, String.class).ifPresent(value -> result.put(key, value));
      }
    }
    return result;
  }
}
