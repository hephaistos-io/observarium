package io.hephaistos.observarium.handler;

import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.event.Severity;
import io.hephaistos.observarium.fingerprint.ExceptionFingerprinter;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.scrub.DataScrubber;
import io.hephaistos.observarium.trace.TraceContextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExceptionProcessor {

    private static final Logger log = LoggerFactory.getLogger(ExceptionProcessor.class);

    private final ExceptionFingerprinter fingerprinter;
    private final DataScrubber scrubber;
    private final TraceContextProvider traceProvider;
    private final List<PostingService> postingServices;

    public ExceptionProcessor(
            ExceptionFingerprinter fingerprinter,
            DataScrubber scrubber,
            TraceContextProvider traceProvider,
            List<PostingService> postingServices) {
        this.fingerprinter = fingerprinter;
        this.scrubber = scrubber;
        this.traceProvider = traceProvider;
        this.postingServices = List.copyOf(postingServices);
    }

    public List<PostingResult> process(Throwable throwable, Severity severity,
                                        Map<String, String> tags) {
        ExceptionEvent event = buildEvent(throwable, severity, tags);
        List<PostingResult> results = new ArrayList<>();

        for (PostingService service : postingServices) {
            try {
                DuplicateSearchResult dup = service.findDuplicate(event);
                if (dup.found()) {
                    log.info("Duplicate found on {} (issue {}), adding comment",
                        service.name(), dup.externalIssueId());
                    results.add(service.commentOnIssue(dup.externalIssueId(), event));
                } else {
                    log.info("No duplicate on {}, creating new issue", service.name());
                    results.add(service.createIssue(event));
                }
            } catch (Exception e) {
                log.error("Failed to post to {}: {}", service.name(), e.getMessage(), e);
                results.add(PostingResult.failure(
                    "Service " + service.name() + ": " + e.getMessage()));
            }
        }
        return results;
    }

    private ExceptionEvent buildEvent(Throwable throwable, Severity severity,
                                       Map<String, String> tags) {
        String fingerprint = fingerprinter.fingerprint(throwable);
        String message = scrubber.scrub(throwable.getMessage());
        String rawTrace = scrubber.scrub(getFullStackTrace(throwable));
        List<String> frames = new ArrayList<>();
        for (StackTraceElement frame : throwable.getStackTrace()) {
            frames.add(frame.toString());
        }

        return ExceptionEvent.builder()
            .fingerprint(fingerprint)
            .exceptionClass(throwable.getClass().getName())
            .message(message)
            .stackTrace(frames)
            .rawStackTrace(rawTrace)
            .severity(severity)
            .timestamp(Instant.now())
            .threadName(Thread.currentThread().getName())
            .traceId(traceProvider.getTraceId())
            .spanId(traceProvider.getSpanId())
            .tags(tags != null ? tags : Map.of())
            .extra(Map.of(
                "java.version", System.getProperty("java.version", "unknown"),
                "os.name", System.getProperty("os.name", "unknown")
            ))
            .build();
    }

    private static String getFullStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
