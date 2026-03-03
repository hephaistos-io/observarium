package io.hephaistos.observarium.email;

import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.event.Severity;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailPostingServiceTest {

    /**
     * Config pointing at a non-existent SMTP server. Transport.send() will fail
     * immediately at connection time, exercising the catch block in createIssue()
     * without requiring a real mail server. Port 0 is never a valid SMTP port, so
     * the OS rejects the connection instantly.
     */
    private static final EmailConfig CONFIG_STARTTLS_ON = new EmailConfig(
        "127.0.0.1",
        0,
        "observarium@example.com",
        "alerts@example.com",
        "user@example.com",
        "secret",
        true
    );

    private static final EmailConfig CONFIG_STARTTLS_OFF = new EmailConfig(
        "127.0.0.1",
        0,
        "observarium@example.com",
        "alerts@example.com",
        "user@example.com",
        "secret",
        false
    );

    // -----------------------------------------------------------------------
    // name()
    // -----------------------------------------------------------------------

    @Test
    void name_returnsEmail() {
        EmailPostingService service = new EmailPostingService(CONFIG_STARTTLS_ON);
        assertEquals("email", service.name());
    }

    // -----------------------------------------------------------------------
    // EmailConfig
    // -----------------------------------------------------------------------

    @Test
    void config_defaultConstructor_setsSensibleDefaults() {
        EmailConfig cfg = new EmailConfig(
            "smtp.example.com",
            "from@example.com",
            "to@example.com",
            "user",
            "pass"
        );
        assertEquals(587, cfg.smtpPort());
        assertTrue(cfg.startTls());
    }

    @Test
    void config_storesAllFields() {
        EmailConfig cfg = new EmailConfig(
            "smtp.example.com",
            587,
            "observarium@example.com",
            "alerts@example.com",
            "user@example.com",
            "secret",
            true
        );
        assertEquals("smtp.example.com", cfg.smtpHost());
        assertEquals(587, cfg.smtpPort());
        assertEquals("observarium@example.com", cfg.from());
        assertEquals("alerts@example.com", cfg.to());
        assertEquals("user@example.com", cfg.username());
        assertEquals("secret", cfg.password());
        assertTrue(cfg.startTls());
    }

    @Test
    void config_startTlsFalse_storesCorrectly() {
        EmailConfig cfg = new EmailConfig(
            "smtp.example.com",
            25,
            "from@example.com",
            "to@example.com",
            "user",
            "pass",
            false
        );
        assertFalse(cfg.startTls());
        assertEquals(25, cfg.smtpPort());
    }

    // -----------------------------------------------------------------------
    // findDuplicate()
    // -----------------------------------------------------------------------

    @Test
    void findDuplicate_alwaysReturnsNotFound() {
        EmailPostingService service = new EmailPostingService(CONFIG_STARTTLS_ON);
        ExceptionEvent event = minimalEvent("fp-001");

        DuplicateSearchResult result = service.findDuplicate(event);

        assertNotNull(result);
        assertFalse(result.found(), "Email posting service must always return notFound for findDuplicate");
        assertNull(result.externalIssueId());
        assertNull(result.url());
    }

    @Test
    void findDuplicate_returnsNotFound_regardlessOfEvent() {
        EmailPostingService service = new EmailPostingService(CONFIG_STARTTLS_ON);

        ExceptionEvent event = ExceptionEvent.builder()
            .fingerprint("long-fingerprint-value-here")
            .exceptionClass("java.lang.NullPointerException")
            .message(null)
            .rawStackTrace("java.lang.NullPointerException\n\tat com.example.Foo.bar(Foo.java:5)")
            .severity(Severity.FATAL)
            .timestamp(Instant.now())
            .threadName("worker-1")
            .traceId("trace-abc-123")
            .spanId("span-xyz-456")
            .tags(Map.of("env", "prod", "version", "1.0.0"))
            .build();

        DuplicateSearchResult result = service.findDuplicate(event);
        assertFalse(result.found());
    }

    // -----------------------------------------------------------------------
    // commentOnIssue()
    // -----------------------------------------------------------------------

    @Test
    void commentOnIssue_alwaysReturnsFailure() {
        EmailPostingService service = new EmailPostingService(CONFIG_STARTTLS_ON);
        ExceptionEvent event = minimalEvent("fp-002");

        PostingResult result = service.commentOnIssue("some-id", event);

        assertNotNull(result);
        assertFalse(result.success(), "Email posting service must return failure for commentOnIssue");
        assertEquals(
            "Email posting service does not support commenting on existing issues",
            result.errorMessage()
        );
    }

    @Test
    void commentOnIssue_returnsFailure_regardlessOfIssueId() {
        EmailPostingService service = new EmailPostingService(CONFIG_STARTTLS_ON);
        ExceptionEvent event = minimalEvent("fp-003");

        PostingResult result = service.commentOnIssue(null, event);

        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    // -----------------------------------------------------------------------
    // createIssue() — exercises buildSession(), buildPlainTextBody(), and the
    // catch block. Transport.send() fails immediately because port 0 is not a
    // valid SMTP port, so no real network connection is attempted.
    // -----------------------------------------------------------------------

    @Test
    void createIssue_returnsFailure_whenSmtpConnectionFails_withStartTlsEnabled() {
        EmailPostingService service = new EmailPostingService(CONFIG_STARTTLS_ON);
        ExceptionEvent event = minimalEvent("fp-send-fail-tls");

        PostingResult result = service.createIssue(event);

        assertFalse(result.success(), "createIssue must return failure when SMTP transport fails");
        assertNotNull(result.errorMessage(), "Failure result must carry an error message");
        assertTrue(
            result.errorMessage().startsWith("Failed to send email:"),
            "Error message must begin with 'Failed to send email:' but was: " + result.errorMessage()
        );
    }

    @Test
    void createIssue_returnsFailure_whenSmtpConnectionFails_withStartTlsDisabled() {
        // Exercises the startTls=false branch of buildSession()
        EmailPostingService service = new EmailPostingService(CONFIG_STARTTLS_OFF);
        ExceptionEvent event = minimalEvent("fp-send-fail-notls");

        PostingResult result = service.createIssue(event);

        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    @Test
    void createIssue_buildsBody_withNullMessageAndNoOptionalFields() {
        // Exercises: null message branch, no traceId, no spanId, empty tags
        EmailPostingService service = new EmailPostingService(CONFIG_STARTTLS_ON);
        ExceptionEvent event = ExceptionEvent.builder()
            .fingerprint("fp-null-msg")
            .exceptionClass("com.example.BareException")
            .message(null)
            .rawStackTrace("com.example.BareException\n\tat com.example.App.run(App.java:1)")
            .severity(Severity.WARNING)
            .timestamp(Instant.parse("2025-01-01T00:00:00Z"))
            .threadName("main")
            .build();

        PostingResult result = service.createIssue(event);

        // The send will fail, but the body was built (no exception before Transport.send)
        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    @Test
    void createIssue_buildsBody_withAllOptionalFields() {
        // Exercises: non-null message, traceId, spanId, non-empty tags
        EmailPostingService service = new EmailPostingService(CONFIG_STARTTLS_ON);
        ExceptionEvent event = ExceptionEvent.builder()
            .fingerprint("fp-all-fields")
            .exceptionClass("java.lang.RuntimeException")
            .message("something went wrong")
            .rawStackTrace("java.lang.RuntimeException: something went wrong\n\tat App.run(App.java:5)")
            .severity(Severity.ERROR)
            .timestamp(Instant.parse("2025-06-15T12:00:00Z"))
            .threadName("request-handler-42")
            .traceId("trace-0000-aaaa")
            .spanId("span-1111-bbbb")
            .tags(Map.of("env", "staging", "region", "eu-west-1"))
            .build();

        PostingResult result = service.createIssue(event);

        // Send fails because we use port 0, but body construction succeeded
        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    @Test
    void createIssue_buildsBody_withTraceIdOnly() {
        // Exercises: traceId present, spanId absent
        EmailPostingService service = new EmailPostingService(CONFIG_STARTTLS_ON);
        ExceptionEvent event = ExceptionEvent.builder()
            .fingerprint("fp-trace-only")
            .exceptionClass("com.example.TracedException")
            .message("traced error")
            .rawStackTrace("com.example.TracedException\n\tat App.go(App.java:10)")
            .severity(Severity.ERROR)
            .timestamp(Instant.now())
            .threadName("async-1")
            .traceId("trace-xyz")
            .build();

        PostingResult result = service.createIssue(event);

        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    @Test
    void createIssue_buildsBody_withSpanIdOnly() {
        // Exercises: traceId absent, spanId present
        EmailPostingService service = new EmailPostingService(CONFIG_STARTTLS_ON);
        ExceptionEvent event = ExceptionEvent.builder()
            .fingerprint("fp-span-only")
            .exceptionClass("com.example.SpannedException")
            .message("span error")
            .rawStackTrace("com.example.SpannedException\n\tat App.go(App.java:20)")
            .severity(Severity.ERROR)
            .timestamp(Instant.now())
            .threadName("async-2")
            .spanId("span-abc")
            .build();

        PostingResult result = service.createIssue(event);

        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private static ExceptionEvent minimalEvent(String fingerprint) {
        return ExceptionEvent.builder()
            .fingerprint(fingerprint)
            .exceptionClass("com.example.TestException")
            .message("test failure")
            .rawStackTrace("com.example.TestException: test failure\n\tat com.example.App.run(App.java:20)")
            .severity(Severity.ERROR)
            .timestamp(Instant.parse("2025-06-01T10:00:00Z"))
            .threadName("test-thread")
            .build();
    }
}
