package io.hephaistos.observarium.quarkus;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

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
}
