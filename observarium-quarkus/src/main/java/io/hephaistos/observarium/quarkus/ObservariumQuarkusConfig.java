package io.hephaistos.observarium.quarkus;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Quarkus-style configuration mapping for Observarium, bound from the {@code observarium.*} config
 * namespace via SmallRye Config.
 *
 * <p>Posting-service-specific configuration (e.g. {@code observarium.github.*}) is handled by the
 * {@link io.hephaistos.observarium.posting.PostingServiceFactory} SPI — each posting module owns
 * its own config keys.
 */
@ConfigMapping(prefix = "observarium")
public interface ObservariumQuarkusConfig {

  @WithDefault("true")
  boolean enabled();

  @WithDefault("BASIC")
  String scrubLevel();

  @WithDefault("trace_id")
  String traceIdMdcKey();

  @WithDefault("span_id")
  String spanIdMdcKey();

  @WithDefault("5")
  int maxDuplicateComments();

  /**
   * Capacity of the bounded queue backing the background worker. Mirrors {@code
   * Builder#queueCapacity}.
   */
  @WithDefault("256")
  int queueCapacity();

  /** Additional regex patterns applied by the default scrubber. Empty when unset. */
  Optional<List<String>> scrubPatterns();

  /** Fully-qualified exception class names to ignore; matches subclasses too. Empty when unset. */
  Optional<List<String>> ignoredExceptions();

  /** Maximum time the queue drain may take on shutdown. Mirrors {@code Builder#shutdownTimeout}. */
  @WithDefault("PT10S")
  Duration shutdownTimeout();

  /**
   * Installs Observarium as the JVM default uncaught exception handler. Off by default: Quarkus
   * catches exceptions on request-handling threads, so this only matters for applications spawning
   * their own unmanaged threads.
   */
  @WithDefault("false")
  boolean installUncaughtHandler();
}
