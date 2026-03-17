package io.hephaistos.observarium.posting;

import static org.junit.jupiter.api.Assertions.*;

import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.event.Severity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IssueFormatterTest {

  private static final String FINGERPRINT = "a".repeat(64);
  private final IssueFormatter formatter = new DefaultIssueFormatter();

  // -----------------------------------------------------------------------
  // helpers
  // -----------------------------------------------------------------------

  private static ExceptionEvent.Builder baseEvent() {
    return ExceptionEvent.builder()
        .fingerprint(FINGERPRINT)
        .exceptionClass("com.example.SomeException")
        .message("Something went wrong")
        .stackTrace(List.of())
        .rawStackTrace(
            "com.example.SomeException: Something went wrong\n\tat com.example.App.main(App.java:10)")
        .severity(Severity.ERROR)
        .timestamp(Instant.parse("2024-01-15T10:30:00Z"))
        .threadName("main")
        .tags(Map.of())
        .extra(Map.of());
  }

  // -----------------------------------------------------------------------
  // title()
  // -----------------------------------------------------------------------

  @Test
  void title_usesShortClassName_notFullyQualifiedName() {
    ExceptionEvent event = baseEvent().build();
    String title = formatter.title(event);

    assertTrue(title.contains("SomeException"), "Title must include the short class name");
    assertFalse(
        title.contains("com.example.SomeException"), "Title must not include the package prefix");
  }

  @Test
  void title_includesMessage_whenShortEnough() {
    ExceptionEvent event = baseEvent().message("Short error").build();
    String title = formatter.title(event);
    assertTrue(title.contains("Short error"));
  }

  @Test
  void title_truncatesLongMessage() {
    // Message is 100 chars — must be truncated to 77 chars + "..."
    String longMsg = "x".repeat(100);
    ExceptionEvent event = baseEvent().message(longMsg).build();
    String title = formatter.title(event);

    // The full 100-char message must not appear verbatim.
    assertFalse(title.contains(longMsg), "Long message must be truncated");
    assertTrue(title.endsWith("..."), "Truncated title must end with '...'");

    // The fragment before "..." should be exactly 77 chars of 'x'.
    String expectedFragment = "x".repeat(77) + "...";
    assertTrue(
        title.contains(expectedFragment), "Truncated portion must be 77 chars followed by '...'");
  }

  @Test
  void title_handlesNullMessage() {
    ExceptionEvent event = baseEvent().message(null).build();
    String title = formatter.title(event);
    assertTrue(
        title.contains("(no message)"), "Null message must produce '(no message)' placeholder");
  }

  @Test
  void title_startsWithObservariumPrefix() {
    ExceptionEvent event = baseEvent().build();
    assertTrue(
        formatter.title(event).startsWith("[Observarium]"),
        "Title must start with the [Observarium] prefix");
  }

  @Test
  void title_exactlyAt80Chars_isNotTruncated() {
    // Message of exactly 80 chars is at the boundary — must NOT be truncated.
    String msg80 = "y".repeat(80);
    ExceptionEvent event = baseEvent().message(msg80).build();
    String title = formatter.title(event);
    assertTrue(title.contains(msg80), "Message of exactly 80 characters must not be truncated");
  }

  @Test
  void title_topLevelClass_noPackage_isUnchanged() {
    // A class name with no dot (no package) should be used as-is.
    ExceptionEvent event = baseEvent().exceptionClass("RawException").build();
    String title = formatter.title(event);
    assertTrue(title.contains("RawException"));
  }

  // -----------------------------------------------------------------------
  // fingerprintMarker()
  // -----------------------------------------------------------------------

  @Test
  void fingerprintMarker_containsFingerprintValue() {
    String marker = formatter.fingerprintMarker(FINGERPRINT);
    assertTrue(marker.contains(FINGERPRINT));
  }

  @Test
  void fingerprintMarker_isHtmlComment() {
    String marker = formatter.fingerprintMarker(FINGERPRINT);
    assertTrue(marker.startsWith("<!--"), "Marker must be an HTML comment");
    assertTrue(marker.endsWith("-->"), "Marker must end the HTML comment");
  }

  // -----------------------------------------------------------------------
  // markdownBody()
  // -----------------------------------------------------------------------

  @Test
  void markdownBody_containsFingerprintMarker() {
    ExceptionEvent event = baseEvent().build();
    String body = formatter.markdownBody(event);
    assertTrue(
        body.contains(formatter.fingerprintMarker(FINGERPRINT)),
        "Markdown body must contain the fingerprint marker comment");
  }

  @Test
  void markdownBody_containsExceptionClass() {
    ExceptionEvent event = baseEvent().build();
    assertTrue(formatter.markdownBody(event).contains("com.example.SomeException"));
  }

  @Test
  void markdownBody_containsStackTrace() {
    ExceptionEvent event = baseEvent().build();
    String body = formatter.markdownBody(event);
    assertTrue(
        body.contains(event.rawStackTrace()), "Markdown body must embed the raw stack trace");
  }

  @Test
  void markdownBody_includesTraceId_whenPresent() {
    ExceptionEvent event = baseEvent().traceId("trace-999").build();
    String body = formatter.markdownBody(event);
    assertTrue(body.contains("trace-999"), "Markdown body must include the trace ID when present");
  }

  @Test
  void markdownBody_omitsTraceId_whenAbsent() {
    ExceptionEvent event = baseEvent().traceId(null).build();
    String body = formatter.markdownBody(event);
    assertFalse(
        body.contains("Trace ID"),
        "Markdown body must not include a trace ID section when trace is null");
  }

  @Test
  void markdownBody_includesSpanId_whenPresent() {
    ExceptionEvent event = baseEvent().spanId("span-456").build();
    String body = formatter.markdownBody(event);
    assertTrue(body.contains("span-456"), "Markdown body must include the span ID when present");
  }

  @Test
  void markdownBody_includesTags_whenPresent() {
    ExceptionEvent event = baseEvent().tags(Map.of("env", "staging")).build();
    String body = formatter.markdownBody(event);
    assertTrue(body.contains("env"), "Markdown body must contain tag key");
    assertTrue(body.contains("staging"), "Markdown body must contain tag value");
  }

  @Test
  void markdownBody_omitsTagsSection_whenEmpty() {
    ExceptionEvent event = baseEvent().tags(Map.of()).build();
    String body = formatter.markdownBody(event);
    assertFalse(
        body.contains("## Tags"),
        "Markdown body must not include Tags section when tags are empty");
  }

  // -----------------------------------------------------------------------
  // markdownComment()
  // -----------------------------------------------------------------------

  @Test
  void markdownComment_containsTimestamp() {
    ExceptionEvent event = baseEvent().build();
    String comment = formatter.markdownComment(event);
    assertTrue(
        comment.contains(event.timestamp().toString()), "Comment must include the event timestamp");
  }

  @Test
  void markdownComment_includesTraceId_whenPresent() {
    ExceptionEvent event = baseEvent().traceId("trace-comment-1").build();
    String comment = formatter.markdownComment(event);
    assertTrue(comment.contains("trace-comment-1"), "Comment must include trace ID when present");
  }

  @Test
  void markdownComment_omitsTraceId_whenAbsent() {
    ExceptionEvent event = baseEvent().traceId(null).build();
    String comment = formatter.markdownComment(event);
    assertFalse(
        comment.contains("Trace ID"),
        "Comment must not include trace ID section when trace is null");
  }

  @Test
  void markdownComment_containsSeverity() {
    ExceptionEvent event = baseEvent().severity(Severity.FATAL).build();
    String comment = formatter.markdownComment(event);
    assertTrue(comment.contains("FATAL"), "Comment must include the severity");
  }

  @Test
  void markdownComment_includesTags_whenPresent() {
    ExceptionEvent event = baseEvent().tags(Map.of("request-id", "req-001")).build();
    String comment = formatter.markdownComment(event);
    assertTrue(comment.contains("req-001"), "Comment must include tags when present");
  }

  @Test
  void markdownComment_startsWithOccurredAgainHeader() {
    ExceptionEvent event = baseEvent().build();
    assertTrue(
        formatter.markdownComment(event).startsWith("## Occurred Again"),
        "Comment must start with the 'Occurred Again' header");
  }

  // -----------------------------------------------------------------------
  // markdownCommentLimitNotice()
  // -----------------------------------------------------------------------

  @Test
  void markdownCommentLimitNotice_containsLimitNumber() {
    String notice = formatter.markdownCommentLimitNotice(5);
    assertTrue(notice.contains("5"), "Limit notice must contain the configured limit number");
  }

  @Test
  void markdownCommentLimitNotice_containsMetricHint() {
    String notice = formatter.markdownCommentLimitNotice(5);
    assertTrue(
        notice.contains("observarium.comments.dropped"),
        "Limit notice must reference the observarium.comments.dropped metric");
  }

  @Test
  void markdownCommentLimitNotice_containsCommentLimitHeader() {
    String notice = formatter.markdownCommentLimitNotice(5);
    assertTrue(
        notice.contains("Comment Limit Reached"),
        "Limit notice must contain the 'Comment Limit Reached' header");
  }
}
