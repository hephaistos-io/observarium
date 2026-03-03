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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class Observarium {

    private static final Logger log = LoggerFactory.getLogger(Observarium.class);
    private static final int DEFAULT_QUEUE_CAPACITY = 256;

    private final ExceptionProcessor processor;
    private final ObservariumConfig config;
    private final ExecutorService executor;

    private Observarium(ExceptionProcessor processor, ObservariumConfig config,
                         ExecutorService executor) {
        this.processor = processor;
        this.config = config;
        this.executor = executor;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("Observarium executor did not terminate in time, forcing shutdown");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }, "observarium-shutdown"));
    }

    public CompletableFuture<List<PostingResult>> captureException(Throwable throwable) {
        return captureException(throwable, Severity.ERROR, Map.of());
    }

    public CompletableFuture<List<PostingResult>> captureException(Throwable throwable,
                                                                     Severity severity) {
        return captureException(throwable, severity, Map.of());
    }

    public CompletableFuture<List<PostingResult>> captureException(Throwable throwable,
                                                                     Severity severity,
                                                                     Map<String, String> tags) {
        return CompletableFuture.supplyAsync(
            () -> processor.process(throwable, severity, tags), executor)
            .exceptionally(ex -> {
                log.error("Failed to process exception", ex);
                return List.of(PostingResult.failure("Processing failed: " + ex.getMessage()));
            });
    }

    public ObservariumConfig config() {
        return config;
    }

    public void shutdown() {
        executor.shutdown();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String apiKey;
        private ScrubLevel scrubLevel = ScrubLevel.BASIC;
        private final List<Pattern> additionalScrubPatterns = new ArrayList<>();
        private ExceptionFingerprinter fingerprinter;
        private DataScrubber scrubber;
        private TraceContextProvider traceProvider;
        private final List<PostingService> postingServices = new ArrayList<>();
        private int queueCapacity = DEFAULT_QUEUE_CAPACITY;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder scrubLevel(ScrubLevel level) {
            this.scrubLevel = level;
            return this;
        }

        public Builder addScrubPattern(Pattern pattern) {
            this.additionalScrubPatterns.add(pattern);
            return this;
        }

        public Builder fingerprinter(ExceptionFingerprinter fingerprinter) {
            this.fingerprinter = fingerprinter;
            return this;
        }

        public Builder scrubber(DataScrubber scrubber) {
            this.scrubber = scrubber;
            return this;
        }

        public Builder traceContextProvider(TraceContextProvider provider) {
            this.traceProvider = provider;
            return this;
        }

        public Builder addPostingService(PostingService service) {
            this.postingServices.add(service);
            return this;
        }

        public Builder postingServices(List<PostingService> services) {
            this.postingServices.clear();
            this.postingServices.addAll(services);
            return this;
        }

        public Builder queueCapacity(int capacity) {
            this.queueCapacity = capacity;
            return this;
        }

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

            var config = new ObservariumConfig(apiKey, scrubLevel, postingServices.size());
            var processor = new ExceptionProcessor(
                fingerprinter, scrubber, traceProvider, postingServices);

            var executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                r -> {
                    Thread t = new Thread(r, "observarium-worker");
                    t.setDaemon(true);
                    return t;
                },
                (r, e) -> log.warn("Observarium queue full, dropping exception report"));

            return new Observarium(processor, config, executor);
        }
    }
}
