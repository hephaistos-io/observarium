package io.hephaistos.observarium.posting;

import io.hephaistos.observarium.event.ExceptionEvent;

/**
 * Default {@link IssueFormatter} that produces markdown-formatted issue content with an
 * HTML-comment fingerprint marker for duplicate detection.
 *
 * <p>Titles use the unqualified exception class name and up to 80 characters of the message. Bodies
 * include exception details, severity, timestamp, thread name, optional tracing IDs, user-supplied
 * tags, and the full raw stack trace. Follow-up comments record the new occurrence without
 * repeating the stack trace.
 *
 * <p>This class is stateless and safe to share across threads.
 */
public class DefaultIssueFormatter implements IssueFormatter {

  @Override
  public String fingerprintMarker(String fingerprint) {
    return "<!-- observarium:fingerprint:" + fingerprint + " -->";
  }

  @Override
  public String title(ExceptionEvent event) {
    String shortClass = event.exceptionClass();
    int lastDot = shortClass.lastIndexOf('.');
    if (lastDot >= 0) {
      shortClass = shortClass.substring(lastDot + 1);
    }
    var message = event.message();
    if (message != null && message.length() > 80) {
      message = message.substring(0, 77) + "...";
    }
    return "[Observarium] " + shortClass + ": " + (message != null ? message : "(no message)");
  }

  @Override
  public String markdownBody(ExceptionEvent event) {
    var body = new StringBuilder();
    body.append(fingerprintMarker(event.fingerprint())).append("\n\n");
    body.append("## Exception\n\n");
    body.append("**Type:** `").append(event.exceptionClass()).append("`\n\n");
    body.append("**Message:** ")
        .append(event.message() != null ? event.message() : "N/A")
        .append("\n\n");
    body.append("**Severity:** ").append(event.severity()).append("\n\n");
    body.append("**Timestamp:** ").append(event.timestamp()).append("\n\n");
    body.append("**Thread:** ").append(event.threadName()).append("\n\n");

    if (event.traceId() != null) {
      body.append("**Trace ID:** `").append(event.traceId()).append("`\n\n");
    }
    if (event.spanId() != null) {
      body.append("**Span ID:** `").append(event.spanId()).append("`\n\n");
    }

    if (!event.tags().isEmpty()) {
      body.append("## Tags\n\n");
      event
          .tags()
          .forEach(
              (key, value) ->
                  body.append("- **").append(key).append(":** ").append(value).append("\n"));
      body.append("\n");
    }

    body.append("## Stack Trace\n\n```\n");
    body.append(event.rawStackTrace());
    body.append("\n```\n\n");

    body.append("**Fingerprint:** `").append(event.fingerprint()).append("`\n");
    return body.toString();
  }

  @Override
  public String markdownComment(ExceptionEvent event) {
    var comment = new StringBuilder();
    comment.append("## Occurred Again\n\n");
    comment.append("**Timestamp:** ").append(event.timestamp()).append("\n\n");
    comment.append("**Thread:** ").append(event.threadName()).append("\n\n");
    if (event.traceId() != null) {
      comment.append("**Trace ID:** `").append(event.traceId()).append("`\n\n");
    }
    if (!event.tags().isEmpty()) {
      comment.append("**Tags:** ").append(event.tags()).append("\n\n");
    }
    comment.append("**Severity:** ").append(event.severity()).append("\n");
    return comment.toString();
  }
}
