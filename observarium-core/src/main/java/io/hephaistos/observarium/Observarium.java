package io.hephaistos.observarium;

import io.hephaistos.observarium.event.Severity;
import io.hephaistos.observarium.fingerprint.DefaultExceptionFingerprinter;
import io.hephaistos.observarium.fingerprint.ExceptionFingerprinter;
import io.hephaistos.observarium.handler.ExceptionProcessor;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.scrub.DataScrubber;
import io.hephaistos.observarium.scrub.DefaultDataScrubber;
import io.hephaistos.observarium.scrub.ScrubLevel;
import io.hephaistos.observarium.trace.MdcTraceContextProvider;
import io.hephaistos.observarium.trace.TraceContextProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for Observarium, an asynchronous exception-reporting library.
 *
 * <p>When an exception is captured, it is placed on an internal bounded queue and processed by a
 * background worker thread. The worker fingerprints the exception for deduplication, scrubs
 * sensitive data from the normalized event, then forwards the event to each configured {@link
 * PostingService}. Each service searches for a duplicate issue and either comments on it or creates
 * a new one.
 *
 * <p>Instances are thread-safe and intended to be shared as application-scoped singletons.
 * Construct one via {@link #builder()}:
 *
 * <pre>{@code
 * Observarium observarium = Observarium.builder()
 *     .scrubLevel(ScrubLevel.STRICT)
 *     .addPostingService(new MyGitHubPostingService(token, repo))
 *     .build();
 *
 * // Capture an exception with default ERROR severity
 * observarium.captureException(ex);
 *
 * // Capture with explicit severity and custom tags
 * observarium.captureException(ex, Severity.WARNING, Map.of("env", "staging"));
 * }</pre>
 *
 * <p>A JVM shutdown hook is registered automatically at construction time to drain the queue
 * gracefully. Call {@link #shutdown()} explicitly only when you need to stop processing before JVM
 * exit (e.g., in a container that reuses the JVM between deployments).
 */
public final class Observarium {

  private static final Logger log = LoggerFactory.getLogger(Observarium.class);
  private static final int DEFAULT_QUEUE_CAPACITY = 256;

  private final ExceptionProcessor processor;
  private final TraceContextProvider traceProvider;
  private final ObservariumConfig config;
  private final ExecutorService executor;

  private Observarium(
      ExceptionProcessor processor,
      TraceContextProvider traceProvider,
      ObservariumConfig config,
      ExecutorService executor) {
    this.processor = processor;
    this.traceProvider = traceProvider;
    this.config = config;
    this.executor = executor;

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  executor.shutdown();
                  try {
                    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                      log.warn("Observarium executor did not terminate in time, forcing shutdown");
                      executor.shutdownNow();
                    }
                  } catch (InterruptedException exception) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                  }
                },
                "observarium-shutdown"));
  }

  /**
   * Captures the given exception with {@link Severity#ERROR} severity and no additional tags.
   *
   * <p>This is a convenience overload for the common case. See {@link #captureException(Throwable,
   * Severity, Map)} for full semantics.
   *
   * @param throwable the exception to report; must not be {@code null}
   * @return a {@link CompletableFuture} that resolves to the list of results from each configured
   *     {@link PostingService}, or a single failure result if processing itself fails; never {@code
   *     null}
   */
  public CompletableFuture<List<PostingResult>> captureException(Throwable throwable) {
    return captureException(throwable, Severity.ERROR, Map.of());
  }

  /**
   * Captures the given exception with the specified severity and no additional tags.
   *
   * <p>See {@link #captureException(Throwable, Severity, Map)} for full semantics.
   *
   * @param throwable the exception to report; must not be {@code null}
   * @param severity the severity to attach to the event; must not be {@code null}
   * @return a {@link CompletableFuture} that resolves to the list of results from each configured
   *     {@link PostingService}; never {@code null}
   */
  public CompletableFuture<List<PostingResult>> captureException(
      Throwable throwable, Severity severity) {
    return captureException(throwable, severity, Map.of());
  }

  /**
   * Captures the given exception asynchronously and routes it through the full reporting pipeline.
   *
   * <p>The caller's trace context (trace ID, span ID) and thread name are captured synchronously on
   * the calling thread before the work is handed off, so the reported event reflects the
   * originating request context rather than the background worker's context.
   *
   * <p>The returned future completes with one {@link PostingResult} per configured {@link
   * PostingService}. If the queue is full the exception is dropped silently and this method will
   * still return a future — the future will complete with a failure result. If no {@link
   * PostingService} is configured the future resolves to an empty list.
   *
   * @param throwable the exception to report; must not be {@code null}
   * @param severity the severity to attach to the event; must not be {@code null}
   * @param tags arbitrary key/value metadata to include in the event; must not be {@code null}, use
   *     an empty map if there are no tags
   * @return a {@link CompletableFuture} that resolves to the list of {@link PostingResult} values
   *     from each configured {@link PostingService}; never {@code null}
   */
  public CompletableFuture<List<PostingResult>> captureException(
      Throwable throwable, Severity severity, Map<String, String> tags) {
    // Eagerly capture thread-local context on the caller's thread before
    // handing off to the async worker where MDC and thread name would differ.
    String callerThreadName = Thread.currentThread().getName();
    String traceId = traceProvider.getTraceId();
    String spanId = traceProvider.getSpanId();

    return CompletableFuture.supplyAsync(
            () -> processor.process(throwable, severity, tags, callerThreadName, traceId, spanId),
            executor)
        .exceptionally(
            ex -> {
              log.error("Failed to process exception", ex);
              return List.of(PostingResult.failure("Processing failed: " + ex.getMessage()));
            });
  }

  /**
   * Returns the read-only configuration snapshot this instance was built with.
   *
   * <p>Useful for introspection in health checks, diagnostic endpoints, or integration tests that
   * need to verify the active scrub level or the number of registered posting services without
   * holding a reference to the builder.
   *
   * @return the active {@link ObservariumConfig}; never {@code null}
   */
  public ObservariumConfig config() {
    return config;
  }

  /**
   * Initiates an orderly shutdown of the background processing thread.
   *
   * <p>Already-queued exceptions continue to be processed; new submissions after this call will be
   * dropped. This method returns immediately — it does not wait for in-flight work to complete.
   *
   * <p>Calling this method is idempotent. A JVM shutdown hook registered at construction time
   * performs the same shutdown automatically on JVM exit, so explicit calls are only needed when
   * you want to stop processing before the JVM exits (e.g., during a controlled reload).
   */
  public void shutdown() {
    executor.shutdown();
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Fluent builder for {@link Observarium}.
   *
   * <p>All settings are optional and have sensible defaults. The only configuration strongly
   * recommended in production is at least one {@link PostingService}; without one, captured
   * exceptions are processed but the results are discarded (a warning is logged at build time).
   */
  public static final class Builder {
    private ScrubLevel scrubLevel = ScrubLevel.BASIC;
    private final List<Pattern> additionalScrubPatterns = new ArrayList<>();
    private ExceptionFingerprinter fingerprinter;
    private DataScrubber scrubber;
    private TraceContextProvider traceProvider;
    private final List<PostingService> postingServices = new ArrayList<>();
    private int queueCapacity = DEFAULT_QUEUE_CAPACITY;

    /**
     * Sets the built-in scrub level applied to all event fields before reporting.
     *
     * <p>Defaults to {@link ScrubLevel#BASIC}. This setting is ignored if a custom {@link
     * DataScrubber} is supplied via {@link #scrubber(DataScrubber)}.
     *
     * @param level the desired scrub level; must not be {@code null}
     * @return this builder
     * @see #addScrubPattern(Pattern)
     * @see #scrubber(DataScrubber)
     */
    public Builder scrubLevel(ScrubLevel level) {
      this.scrubLevel = level;
      return this;
    }

    /**
     * Adds a custom regex pattern to the default scrubber's redaction list.
     *
     * <p>Matched occurrences in any event field are replaced with {@code [REDACTED]}. Use this to
     * redact domain-specific sensitive values (e.g., internal IDs, session tokens) on top of the
     * built-in {@link ScrubLevel} rules.
     *
     * <p>This setting is ignored if a custom {@link DataScrubber} is supplied via {@link
     * #scrubber(DataScrubber)}.
     *
     * @param pattern the pattern to redact; must not be {@code null}
     * @return this builder
     * @see #scrubLevel(ScrubLevel)
     */
    public Builder addScrubPattern(Pattern pattern) {
      this.additionalScrubPatterns.add(pattern);
      return this;
    }

    /**
     * Overrides the exception fingerprinter used for deduplication.
     *
     * <p>If not set, {@link DefaultExceptionFingerprinter} is used, which derives the fingerprint
     * from the exception type and stack trace. Supply a custom implementation when your
     * deduplication requirements differ (e.g., ignoring line numbers for minified code, or
     * incorporating a domain-specific error code).
     *
     * @param fingerprinter the fingerprinter to use; must not be {@code null}
     * @return this builder
     */
    public Builder fingerprinter(ExceptionFingerprinter fingerprinter) {
      this.fingerprinter = fingerprinter;
      return this;
    }

    /**
     * Replaces the default {@link DataScrubber} entirely with a custom implementation.
     *
     * <p>When this is set, {@link #scrubLevel(ScrubLevel)} and {@link #addScrubPattern(Pattern)}
     * have no effect because the default scrubber is not constructed at all. Use this when you need
     * full control over redaction logic.
     *
     * @param scrubber the scrubber to use; must not be {@code null}
     * @return this builder
     */
    public Builder scrubber(DataScrubber scrubber) {
      this.scrubber = scrubber;
      return this;
    }

    /**
     * Overrides the provider used to read the current trace context (trace ID and span ID) at
     * capture time.
     *
     * <p>If not set, {@link MdcTraceContextProvider} is used, which reads {@code traceId} and
     * {@code spanId} keys from SLF4J MDC. Override this when your application uses a different
     * distributed tracing mechanism.
     *
     * @param provider the trace context provider; must not be {@code null}
     * @return this builder
     */
    public Builder traceContextProvider(TraceContextProvider provider) {
      this.traceProvider = provider;
      return this;
    }

    /**
     * Registers a single {@link PostingService} to receive processed exception events.
     *
     * <p>Multiple posting services can be registered by calling this method repeatedly. Events are
     * dispatched to all registered services in registration order.
     *
     * @param service the posting service to add; must not be {@code null}
     * @return this builder
     * @see #postingServices(List)
     */
    public Builder addPostingService(PostingService service) {
      this.postingServices.add(service);
      return this;
    }

    /**
     * Replaces all currently registered posting services with the given list.
     *
     * <p>Useful when assembling the list programmatically before passing it to the builder. This
     * clears any services previously added via {@link #addPostingService(PostingService)}.
     *
     * @param services the posting services to use; must not be {@code null} or empty in production
     *     (a warning is logged at build time if the list is empty)
     * @return this builder
     */
    public Builder postingServices(List<PostingService> services) {
      this.postingServices.clear();
      this.postingServices.addAll(services);
      return this;
    }

    /**
     * Sets the maximum number of exception reports that can be buffered in the internal queue
     * awaiting the background worker thread.
     *
     * <p>This is the primary backpressure mechanism. When the queue is full, newly captured
     * exceptions are dropped and a warning is logged — they are not blocked or retried. Defaults to
     * 256. Increase this value if your application generates bursts of exceptions faster than
     * posting services can process them.
     *
     * @param capacity the maximum queue depth; must be greater than zero
     * @return this builder
     */
    public Builder queueCapacity(int capacity) {
      this.queueCapacity = capacity;
      return this;
    }

    /**
     * Builds and returns the configured {@link Observarium} instance.
     *
     * <p>Defaults are applied for any unset options: {@link DefaultExceptionFingerprinter} for
     * fingerprinting, {@link DefaultDataScrubber} with the configured {@link ScrubLevel} for
     * scrubbing, and {@link MdcTraceContextProvider} for trace context. A JVM shutdown hook is
     * registered on the returned instance.
     *
     * @return a fully configured {@link Observarium}; never {@code null}
     */
    public Observarium build() {
      if (fingerprinter == null) {
        fingerprinter = new DefaultExceptionFingerprinter();
      }
      if (scrubber == null) {
        scrubber = new DefaultDataScrubber(scrubLevel, additionalScrubPatterns);
      }
      if (traceProvider == null) {
        traceProvider = new MdcTraceContextProvider();
      }

      if (postingServices.isEmpty()) {
        log.warn("No PostingService configured — captured exceptions will be silently ignored");
      }

      var config = new ObservariumConfig(scrubLevel, postingServices.size());
      var processor = new ExceptionProcessor(fingerprinter, scrubber, postingServices);

      var executor =
          new ThreadPoolExecutor(
              1,
              1,
              0L,
              TimeUnit.MILLISECONDS,
              new ArrayBlockingQueue<>(queueCapacity),
              runnable -> {
                var thread = new Thread(runnable, "observarium-worker");
                thread.setDaemon(true);
                return thread;
              },
              (rejectedTask, rejectedExecutor) ->
                  log.warn("Observarium queue full, dropping exception report"));

      return new Observarium(processor, traceProvider, config, executor);
    }
  }
}
