package io.hephaistos.observarium.spring;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.scrub.ScrubLevel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
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
  private int queueCapacity = 256;
  private List<String> scrubPatterns = Collections.emptyList();
  private List<Pattern> compiledScrubPatterns = Collections.emptyList();
  private List<String> ignoredExceptions = Collections.emptyList();
  private Duration shutdownTimeout = Observarium.DEFAULT_SHUTDOWN_TIMEOUT;
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

  public int getQueueCapacity() {
    return queueCapacity;
  }

  public void setQueueCapacity(int queueCapacity) {
    if (queueCapacity <= 0) {
      throw new IllegalArgumentException(
          "observarium.queue-capacity must be greater than zero, got: " + queueCapacity);
    }
    this.queueCapacity = queueCapacity;
  }

  public List<String> getScrubPatterns() {
    return scrubPatterns;
  }

  /**
   * Sets the additional regex patterns applied by the default scrubber, one per entry.
   *
   * <p>Each pattern is compiled immediately so that an invalid regex fails configuration binding at
   * startup — naming the offending pattern — rather than surfacing only as a log line the first
   * time an exception is captured on a background thread.
   *
   * @param scrubPatterns the regex patterns to compile and add
   */
  public void setScrubPatterns(List<String> scrubPatterns) {
    List<String> source = scrubPatterns == null ? List.of() : scrubPatterns;
    List<Pattern> compiled = new ArrayList<>(source.size());
    for (String pattern : source) {
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
    this.scrubPatterns = List.copyOf(source);
    this.compiledScrubPatterns = List.copyOf(compiled);
  }

  /** Returns the patterns from {@link #getScrubPatterns()}, already compiled. */
  public List<Pattern> getCompiledScrubPatterns() {
    return compiledScrubPatterns;
  }

  public List<String> getIgnoredExceptions() {
    return ignoredExceptions;
  }

  /**
   * Sets the fully-qualified exception class names to ignore. A captured throwable is ignored when
   * its class, or any superclass or implemented interface, matches one of these names.
   *
   * @param ignoredExceptions the FQCNs to ignore
   */
  public void setIgnoredExceptions(List<String> ignoredExceptions) {
    this.ignoredExceptions = ignoredExceptions == null ? List.of() : List.copyOf(ignoredExceptions);
  }

  public Duration getShutdownTimeout() {
    return shutdownTimeout;
  }

  /**
   * Sets the maximum time the {@code SmartLifecycle}-driven shutdown will block draining the queue.
   * Defaults to {@link Observarium#DEFAULT_SHUTDOWN_TIMEOUT} (10 seconds).
   *
   * @param shutdownTimeout the drain budget; must be positive
   */
  public void setShutdownTimeout(Duration shutdownTimeout) {
    if (shutdownTimeout == null || shutdownTimeout.isNegative() || shutdownTimeout.isZero()) {
      throw new IllegalArgumentException(
          "observarium.shutdown-timeout must be a positive duration, got: " + shutdownTimeout);
    }
    this.shutdownTimeout = shutdownTimeout;
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
