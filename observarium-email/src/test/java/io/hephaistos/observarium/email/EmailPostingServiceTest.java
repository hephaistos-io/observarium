package io.hephaistos.observarium.email;

import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.event.Severity;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class EmailPostingServiceTest {

    private static final EmailConfig CONFIG = new EmailConfig(
        "smtp.example.com",
        587,
        "observarium@example.com",
        "alerts@example.com",
        "user@example.com",
        "secret",
        true
    );

    @Test
    void name_returnsEmail() {
        EmailPostingService service = new EmailPostingService(CONFIG);
        assertEquals("email", service.name());
    }

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
        assertEquals(true, cfg.startTls());
    }

    @Test
    void config_storesAllFields() {
        assertEquals("smtp.example.com", CONFIG.smtpHost());
        assertEquals(587, CONFIG.smtpPort());
        assertEquals("observarium@example.com", CONFIG.from());
        assertEquals("alerts@example.com", CONFIG.to());
        assertEquals("user@example.com", CONFIG.username());
        assertEquals("secret", CONFIG.password());
        assertEquals(true, CONFIG.startTls());
    }

    @Test
    void findDuplicate_alwaysReturnsNotFound() {
        EmailPostingService service = new EmailPostingService(CONFIG);
        ExceptionEvent event = minimalEvent("fp-001");

        DuplicateSearchResult result = service.findDuplicate(event);

        assertNotNull(result);
        assertFalse(result.found(), "Email posting service must always return notFound for findDuplicate");
        assertNull(result.externalIssueId());
        assertNull(result.url());
    }

    @Test
    void commentOnIssue_alwaysReturnsFailure() {
        EmailPostingService service = new EmailPostingService(CONFIG);
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
        EmailPostingService service = new EmailPostingService(CONFIG);
        ExceptionEvent event = minimalEvent("fp-003");

        // Null issue ID — should still return a clean failure, not throw
        PostingResult result = service.commentOnIssue(null, event);

        assertFalse(result.success());
        assertNotNull(result.errorMessage());
    }

    @Test
    void findDuplicate_returnsNotFound_regardlessOfEvent() {
        EmailPostingService service = new EmailPostingService(CONFIG);

        // Event with extra data — should still return notFound without throwing
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
            .tags(java.util.Map.of("env", "prod", "version", "1.0.0"))
            .build();

        DuplicateSearchResult result = service.findDuplicate(event);
        assertFalse(result.found());
    }

    // --- helpers ---

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
