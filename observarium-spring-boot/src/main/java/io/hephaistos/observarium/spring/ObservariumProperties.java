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
  private int maxDuplicateComments = 5;
  private boolean installUncaughtHandler = false;
  private final Mvc mvc = new Mvc();

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

  public int getMaxDuplicateComments() {
    return maxDuplicateComments;
  }

  public void setMaxDuplicateComments(int maxDuplicateComments) {
    if (maxDuplicateComments < -1 || maxDuplicateComments == 0) {
      throw new IllegalArgumentException(
          "observarium.max-duplicate-comments must be -1 (unlimited) or a positive integer, got: "
              + maxDuplicateComments);
    }
    this.maxDuplicateComments = maxDuplicateComments;
  }

  public boolean isInstallUncaughtHandler() {
    return installUncaughtHandler;
  }

  public void setInstallUncaughtHandler(boolean installUncaughtHandler) {
    this.installUncaughtHandler = installUncaughtHandler;
  }

  public Mvc getMvc() {
    return mvc;
  }

  /** Nested {@code observarium.mvc.*} properties. */
  public static class Mvc {

    private boolean adviceEnabled = true;

    public boolean isAdviceEnabled() {
      return adviceEnabled;
    }

    public void setAdviceEnabled(boolean adviceEnabled) {
      this.adviceEnabled = adviceEnabled;
    }
  }
}
