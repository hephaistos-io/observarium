package io.hephaistos.observarium.event;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ExceptionEvent(
    String fingerprint,
    String exceptionClass,
    String message,
    List<String> stackTrace,
    String rawStackTrace,
    Severity severity,
    Instant timestamp,
    String threadName,
    String traceId,
    String spanId,
    Map<String, String> tags,
    Map<String, String> extra
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String fingerprint;
        private String exceptionClass;
        private String message;
        private List<String> stackTrace = List.of();
        private String rawStackTrace;
        private Severity severity = Severity.ERROR;
        private Instant timestamp = Instant.now();
        private String threadName;
        private String traceId;
        private String spanId;
        private Map<String, String> tags = Map.of();
        private Map<String, String> extra = Map.of();

        public Builder fingerprint(String fingerprint) { this.fingerprint = fingerprint; return this; }
        public Builder exceptionClass(String exceptionClass) { this.exceptionClass = exceptionClass; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder stackTrace(List<String> stackTrace) { this.stackTrace = List.copyOf(stackTrace); return this; }
        public Builder rawStackTrace(String rawStackTrace) { this.rawStackTrace = rawStackTrace; return this; }
        public Builder severity(Severity severity) { this.severity = severity; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }
        public Builder threadName(String threadName) { this.threadName = threadName; return this; }
        public Builder traceId(String traceId) { this.traceId = traceId; return this; }
        public Builder spanId(String spanId) { this.spanId = spanId; return this; }
        public Builder tags(Map<String, String> tags) { this.tags = Map.copyOf(tags); return this; }
        public Builder extra(Map<String, String> extra) { this.extra = Map.copyOf(extra); return this; }

        public ExceptionEvent build() {
            return new ExceptionEvent(
                fingerprint, exceptionClass, message, stackTrace,
                rawStackTrace, severity, timestamp, threadName,
                traceId, spanId, tags, extra
            );
        }
    }
}
