package io.hephaistos.observarium.micrometer;

import io.hephaistos.observarium.ObservariumListener;
import io.hephaistos.observarium.event.Severity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Bridges Observarium's internal lifecycle events to Micrometer meters.
 *
 * <p>This class implements both {@link ObservariumListener} (to receive callbacks from the core
 * pipeline) and {@link MeterBinder} (to register meters with a {@link MeterRegistry}). It exposes
 * the following meters:
 *
 * <table>
 *   <caption>Observarium Meters</caption>
 *   <tr><th>Name</th><th>Type</th><th>Tags</th><th>Description</th></tr>
 *   <tr><td>{@code observarium.exceptions.captured}</td><td>Counter</td><td>{@code severity}</td>
 *       <td>Total exceptions captured</td></tr>
 *   <tr><td>{@code observarium.exceptions.dropped}</td><td>Counter</td><td>—</td>
 *       <td>Exceptions dropped due to full queue</td></tr>
 *   <tr><td>{@code observarium.queue.size}</td><td>Gauge</td><td>—</td>
 *       <td>Current number of items in the processing queue</td></tr>
 *   <tr><td>{@code observarium.posting.duration}</td><td>Timer</td>
 *       <td>{@code service}, {@code action}, {@code outcome}</td>
 *       <td>Time spent posting to each service</td></tr>
 * </table>
 *
 * <p>Instances are thread-safe. Listener callbacks that arrive before {@link #bindTo} has been
 * called are silently ignored (no exceptions, no lost data that could be captured later).
 *
 * <p>Timers and counters are created lazily and cached in concurrent maps keyed by tag
 * combinations. The number of entries is bounded by the number of distinct {@link
 * io.hephaistos.observarium.posting.PostingService} names (registered at build time) multiplied by
 * the fixed action/outcome/severity dimensions. Avoid passing dynamic or user-controlled values as
 * service names, as each unique combination creates a new meter that is never removed.
 *
 * <p>Call {@link #close()} to remove all registered meters from the registry (e.g., during a Spring
 * context refresh or hot-reload). Spring Boot automatically invokes {@code close()} on bean
 * destruction since this class implements {@link AutoCloseable}.
 */
public class ObservariumMeterBinder implements ObservariumListener, MeterBinder, AutoCloseable {

  private final Iterable<Tag> commonTags;

  private volatile MeterRegistry registry;
  private final ConcurrentHashMap<String, Counter> capturedCounters = new ConcurrentHashMap<>();
  private volatile Counter droppedCounter;
  private final ConcurrentHashMap<String, Counter> commentDroppedCounters =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Timer> postingTimers = new ConcurrentHashMap<>();

  private final Object gaugeLock = new Object();
  private Supplier<Integer> queueSizeSupplier;
  private boolean gaugeRegistered;

  /** Creates a binder with no common tags. */
  public ObservariumMeterBinder() {
    this(Tags.empty());
  }

  /**
   * Creates a binder that applies the given tags to every meter it registers.
   *
   * <p>Use this to attach environment-level labels (e.g., {@code application.name}, {@code env})
   * directly to Observarium meters, beyond what Micrometer's global common tags provide.
   *
   * @param commonTags tags to append to every meter; must not be {@code null}
   */
  public ObservariumMeterBinder(Iterable<Tag> commonTags) {
    this.commonTags = commonTags;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    this.registry = registry;
    this.droppedCounter =
        Counter.builder("observarium.exceptions.dropped")
            .description("Exceptions dropped because the processing queue was full")
            .tags(commonTags)
            .register(registry);

    maybeRegisterQueueGauge(registry);
  }

  @Override
  public void onQueueSizeAvailable(Supplier<Integer> supplier) {
    synchronized (gaugeLock) {
      this.queueSizeSupplier = supplier;
    }
    MeterRegistry currentRegistry = this.registry;
    if (currentRegistry != null) {
      maybeRegisterQueueGauge(currentRegistry);
    }
  }

  private void maybeRegisterQueueGauge(MeterRegistry registry) {
    synchronized (gaugeLock) {
      if (gaugeRegistered || queueSizeSupplier == null) {
        return;
      }
      Supplier<Integer> supplier = queueSizeSupplier;
      Gauge.builder("observarium.queue.size", supplier::get)
          .description("Current number of exceptions queued for processing")
          .tags(commonTags)
          .register(registry);
      gaugeRegistered = true;
    }
  }

  @Override
  public void onExceptionCaptured(Severity severity) {
    if (registry == null) {
      return;
    }
    String severityTag = severity.name().toLowerCase();
    capturedCounters
        .computeIfAbsent(
            severityTag,
            tag ->
                Counter.builder("observarium.exceptions.captured")
                    .description("Total exceptions captured by Observarium")
                    .tag("severity", tag)
                    .tags(commonTags)
                    .register(registry))
        .increment();
  }

  @Override
  public void onExceptionDropped() {
    if (droppedCounter != null) {
      droppedCounter.increment();
    }
  }

  @Override
  public void onCommentDropped(String serviceName) {
    if (registry == null) {
      return;
    }
    commentDroppedCounters
        .computeIfAbsent(
            serviceName,
            name ->
                Counter.builder("observarium.comments.dropped")
                    .description(
                        "Duplicate comments suppressed because the issue reached its comment limit")
                    .tag("service", name)
                    .tags(commonTags)
                    .register(registry))
        .increment();
  }

  @Override
  public void onPostingCompleted(
      String serviceName, boolean duplicate, boolean success, long durationNanos) {
    if (registry == null) {
      return;
    }
    String action = duplicate ? "comment" : "create";
    String outcome = success ? "success" : "failure";
    String timerKey = serviceName + ":" + action + ":" + outcome;
    postingTimers
        .computeIfAbsent(
            timerKey,
            key ->
                Timer.builder("observarium.posting.duration")
                    .description("Time spent posting exceptions to issue trackers")
                    .tag("service", serviceName)
                    .tag("action", action)
                    .tag("outcome", outcome)
                    .tags(commonTags)
                    .register(registry))
        .record(durationNanos, TimeUnit.NANOSECONDS);
  }

  /**
   * Removes all meters registered by this binder from the registry and resets internal state.
   *
   * <p>After calling this method, the binder is effectively unbound and will silently ignore
   * listener callbacks until {@link #bindTo} is called again.
   */
  @Override
  public void close() {
    MeterRegistry reg = this.registry;
    if (reg == null) {
      return;
    }

    capturedCounters.values().forEach(reg::remove);
    capturedCounters.clear();

    if (droppedCounter != null) {
      reg.remove(droppedCounter);
      droppedCounter = null;
    }

    commentDroppedCounters.values().forEach(reg::remove);
    commentDroppedCounters.clear();

    postingTimers.values().forEach(reg::remove);
    postingTimers.clear();

    synchronized (gaugeLock) {
      if (gaugeRegistered) {
        reg.find("observarium.queue.size").gauges().forEach(reg::remove);
        gaugeRegistered = false;
      }
    }

    this.registry = null;
  }
}
