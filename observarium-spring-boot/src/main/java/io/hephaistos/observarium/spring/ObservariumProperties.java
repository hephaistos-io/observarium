package io.hephaistos.observarium.spring;

import io.hephaistos.observarium.scrub.ScrubLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Observarium, bound from the {@code observarium.*} namespace.
 *
 * <p>Posting-service-specific configuration (e.g. {@code observarium.github.*}) is handled by the
 * {@link io.hephaistos.observarium.posting.PostingServiceFactory} SPI — each posting module owns
 * its own config keys.
 */
@ConfigurationProperties(prefix = "observarium")
public class ObservariumProperties {

  private boolean enabled = true;
  private ScrubLevel scrubLevel = ScrubLevel.BASIC;
  private String traceIdMdcKey = "trace_id";
  private String spanIdMdcKey = "span_id";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public ScrubLevel getScrubLevel() {
    return scrubLevel;
  }

  public void setScrubLevel(ScrubLevel scrubLevel) {
    this.scrubLevel = scrubLevel;
  }

  public String getTraceIdMdcKey() {
    return traceIdMdcKey;
  }

  public void setTraceIdMdcKey(String traceIdMdcKey) {
    this.traceIdMdcKey = traceIdMdcKey;
  }

  public String getSpanIdMdcKey() {
    return spanIdMdcKey;
  }

  public void setSpanIdMdcKey(String spanIdMdcKey) {
    this.spanIdMdcKey = spanIdMdcKey;
  }
}
