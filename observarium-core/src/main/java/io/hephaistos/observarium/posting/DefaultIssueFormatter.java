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
    return "<!-- observarium:fingerprint:%s -->".formatted(fingerprint);
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
    var message = event.message() != null ? event.message() : "N/A";

    var extras = new StringBuilder();
    if (event.traceId() != null) {
      extras.append("**Trace ID:** `%s`\n\n".formatted(event.traceId()));
    }
    if (event.spanId() != null) {
      extras.append("**Span ID:** `%s`\n\n".formatted(event.spanId()));
    }

    var tags = "";
    if (!event.tags().isEmpty()) {
      var tagSection = new StringBuilder("## Tags\n\n");
      event
          .tags()
          .forEach((key, value) -> tagSection.append("- **%s:** %s\n".formatted(key, value)));
      tagSection.append("\n");
      tags = tagSection.toString();
    }

    return """
        %s

        ## Exception

        **Type:** `%s`

        **Message:** %s

        **Severity:** %s

        **Timestamp:** %s

        **Thread:** %s

        %s%s## Stack Trace

        ```
        %s
        ```

        **Fingerprint:** `%s`
        """
        .formatted(
            fingerprintMarker(event.fingerprint()),
            event.exceptionClass(),
            message,
            event.severity(),
            event.timestamp(),
            event.threadName(),
            extras,
            tags,
            event.rawStackTrace(),
            event.fingerprint());
  }

  @Override
  public String markdownComment(ExceptionEvent event) {
    var traceId =
        event.traceId() != null ? "**Trace ID:** `%s`\n\n".formatted(event.traceId()) : "";
    var tags = !event.tags().isEmpty() ? "**Tags:** %s\n\n".formatted(event.tags()) : "";
    return """
        ## Occurred Again

        **Timestamp:** %s

        **Thread:** %s

        %s%s**Severity:** %s
        """
        .formatted(event.timestamp(), event.threadName(), traceId, tags, event.severity());
  }
}
