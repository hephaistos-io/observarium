package io.hephaistos.observarium.handler;

import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.event.Severity;
import io.hephaistos.observarium.fingerprint.ExceptionFingerprinter;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.scrub.DataScrubber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the fingerprint -&gt; event build -&gt; scrub -&gt; post pipeline for a single exception.
 *
 * <p>This is an internal component; consumers interact with the library through
 * {@link io.hephaistos.observarium.Observarium} rather than this class directly.
 * Each call to {@link #process} fans out to every configured {@link PostingService},
 * collecting one {@link PostingResult} per service regardless of individual failures.
 *
 * <p>Instances are safe to share across threads provided the injected collaborators
 * ({@link ExceptionFingerprinter}, {@link DataScrubber}, and each {@link PostingService}) are
 * themselves thread-safe.
 */
public class ExceptionProcessor {

    private static final Logger log = LoggerFactory.getLogger(ExceptionProcessor.class);

    private final ExceptionFingerprinter fingerprinter;
    private final DataScrubber scrubber;
    private final List<PostingService> postingServices;

    /**
     * Creates a new processor with the given collaborators.
     *
     * @param fingerprinter   produces a stable fingerprint for deduplication across services
     * @param scrubber        redacts sensitive data from the exception message and stack trace
     * @param postingServices the services to which processed events will be posted; the list is
     *                        copied defensively and must not be null
     */
    public ExceptionProcessor(
            ExceptionFingerprinter fingerprinter,
            DataScrubber scrubber,
            List<PostingService> postingServices) {
        this.fingerprinter = fingerprinter;
        this.scrubber = scrubber;
        this.postingServices = List.copyOf(postingServices);
    }

    /**
     * Runs the full reporting pipeline for the given exception and returns one
     * {@link PostingResult} per configured {@link PostingService}.
     *
     * <p>For each service the pipeline checks for a duplicate issue first. When a
     * duplicate is found the exception is appended as a comment; otherwise a new
     * issue is created. A service failure is captured as a {@link PostingResult#failure}
     * entry so that remaining services are still attempted.
     *
     * @param throwable        the exception to report; must not be null
     * @param severity         the caller-assigned severity of the exception; must not be null
     * @param tags             arbitrary key-value metadata to attach to the event; may be null,
     *                         which is treated as an empty map
     * @param callerThreadName name of the originating thread, captured before the async handoff
     *                         because thread names are not available on the worker thread
     * @param traceId          distributed trace ID from the originating thread's MDC context,
     *                         captured eagerly because MDC is thread-local; may be null
     * @param spanId           distributed span ID from the originating thread's MDC context,
     *                         captured eagerly because MDC is thread-local; may be null
     * @return a non-null, non-empty list of results in the same order as the configured
     *         posting services; never throws
     */
    public List<PostingResult> process(Throwable throwable, Severity severity,
                                        Map<String, String> tags,
                                        String callerThreadName,
                                        String traceId, String spanId) {
        var event = buildEvent(throwable, severity, tags,
                callerThreadName, traceId, spanId);
        var results = new ArrayList<PostingResult>();

        for (PostingService service : postingServices) {
            try {
                var duplicate = service.findDuplicate(event);
                if (duplicate.found()) {
                    log.info("Duplicate found on {} (issue {}), adding comment",
                        service.name(), duplicate.externalIssueId());
                    results.add(service.commentOnIssue(duplicate.externalIssueId(), event));
                } else {
                    log.info("No duplicate on {}, creating new issue", service.name());
                    results.add(service.createIssue(event));
                }
            } catch (Exception exception) {
                log.error("Failed to post to {}: {}", service.name(), exception.getMessage(), exception);
                results.add(PostingResult.failure(
                    "Service " + service.name() + ": " + exception.getMessage()));
            }
        }
        return results;
    }

    private ExceptionEvent buildEvent(Throwable throwable, Severity severity,
                                       Map<String, String> tags,
                                       String callerThreadName,
                                       String traceId, String spanId) {
        var fingerprint = fingerprinter.fingerprint(throwable);
        var message = scrubber.scrub(throwable.getMessage());
        var rawStackTrace = scrubber.scrub(getFullStackTrace(throwable));
        var frames = new ArrayList<String>();
        for (StackTraceElement frame : throwable.getStackTrace()) {
            frames.add(frame.toString());
        }

        return ExceptionEvent.builder()
            .fingerprint(fingerprint)
            .exceptionClass(throwable.getClass().getName())
            .message(message)
            .stackTrace(frames)
            .rawStackTrace(rawStackTrace)
            .severity(severity)
            .timestamp(Instant.now())
            .threadName(callerThreadName)
            .traceId(traceId)
            .spanId(spanId)
            .tags(tags != null ? tags : Map.of())
            .extra(Map.of(
                "java.version", System.getProperty("java.version", "unknown"),
                "os.name", System.getProperty("os.name", "unknown")
            ))
            .build();
    }

    private static String getFullStackTrace(Throwable throwable) {
        var stackWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stackWriter));
        return stackWriter.toString();
    }
}
