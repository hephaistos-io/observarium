package io.hephaistos.observarium.posting;

import io.hephaistos.observarium.event.ExceptionEvent;

public final class IssueFormatter {

    private IssueFormatter() {}

    public static String fingerprintMarker(String fingerprint) {
        return "<!-- observarium:fingerprint:" + fingerprint + " -->";
    }

    public static String title(ExceptionEvent event) {
        String shortClass = event.exceptionClass();
        int lastDot = shortClass.lastIndexOf('.');
        if (lastDot >= 0) {
            shortClass = shortClass.substring(lastDot + 1);
        }
        String msg = event.message();
        if (msg != null && msg.length() > 80) {
            msg = msg.substring(0, 77) + "...";
        }
        return "[Observarium] " + shortClass + ": " + (msg != null ? msg : "(no message)");
    }

    public static String markdownBody(ExceptionEvent event) {
        var sb = new StringBuilder();
        sb.append(fingerprintMarker(event.fingerprint())).append("\n\n");
        sb.append("## Exception\n\n");
        sb.append("**Type:** `").append(event.exceptionClass()).append("`\n\n");
        sb.append("**Message:** ").append(event.message() != null ? event.message() : "N/A").append("\n\n");
        sb.append("**Severity:** ").append(event.severity()).append("\n\n");
        sb.append("**Timestamp:** ").append(event.timestamp()).append("\n\n");
        sb.append("**Thread:** ").append(event.threadName()).append("\n\n");

        if (event.traceId() != null) {
            sb.append("**Trace ID:** `").append(event.traceId()).append("`\n\n");
        }
        if (event.spanId() != null) {
            sb.append("**Span ID:** `").append(event.spanId()).append("`\n\n");
        }

        if (!event.tags().isEmpty()) {
            sb.append("## Tags\n\n");
            event.tags().forEach((k, v) -> sb.append("- **").append(k).append(":** ").append(v).append("\n"));
            sb.append("\n");
        }

        sb.append("## Stack Trace\n\n```\n");
        sb.append(event.rawStackTrace());
        sb.append("\n```\n\n");

        sb.append("**Fingerprint:** `").append(event.fingerprint()).append("`\n");
        return sb.toString();
    }

    public static String markdownComment(ExceptionEvent event) {
        var sb = new StringBuilder();
        sb.append("## Occurred Again\n\n");
        sb.append("**Timestamp:** ").append(event.timestamp()).append("\n\n");
        sb.append("**Thread:** ").append(event.threadName()).append("\n\n");
        if (event.traceId() != null) {
            sb.append("**Trace ID:** `").append(event.traceId()).append("`\n\n");
        }
        if (!event.tags().isEmpty()) {
            sb.append("**Tags:** ").append(event.tags()).append("\n\n");
        }
        sb.append("**Severity:** ").append(event.severity()).append("\n");
        return sb.toString();
    }
}
