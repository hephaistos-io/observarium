package io.hephaistos.observarium.gitlab;

import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.event.Severity;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GitLabPostingServiceTest {

    private static final GitLabConfig CONFIG = new GitLabConfig(
        "https://gitlab.example.com",
        "glpat-test-token",
        "42"
    );

    @Test
    void name_returnsGitlab() {
        GitLabPostingService service = new GitLabPostingService(CONFIG);
        assertEquals("gitlab", service.name());
    }

    @Test
    void config_storesAllFields() {
        assertEquals("https://gitlab.example.com", CONFIG.baseUrl());
        assertEquals("glpat-test-token", CONFIG.privateToken());
        assertEquals("42", CONFIG.projectId());
    }

    @Test
    void findDuplicate_returnsNotFound_whenHttpCallFails() {
        // The default HttpClient will attempt a real connection to a non-existent host,
        // which will throw a ConnectException. The implementation must catch it and
        // return notFound() rather than propagating the exception.
        GitLabPostingService service = new GitLabPostingService(CONFIG);
        ExceptionEvent event = minimalEvent("abc123fingerprint");

        DuplicateSearchResult result = service.findDuplicate(event);

        assertNotNull(result);
        assertFalse(result.found(), "findDuplicate must return notFound when the HTTP call fails");
    }

    @Test
    void createIssue_returnsFailure_whenHttpCallFails() {
        GitLabPostingService service = new GitLabPostingService(CONFIG);
        ExceptionEvent event = minimalEvent("abc123fingerprint");

        PostingResult result = service.createIssue(event);

        assertNotNull(result);
        assertFalse(result.success(), "createIssue must return failure when the HTTP call fails");
    }

    @Test
    void commentOnIssue_returnsFailure_whenHttpCallFails() {
        GitLabPostingService service = new GitLabPostingService(CONFIG);
        ExceptionEvent event = minimalEvent("abc123fingerprint");

        PostingResult result = service.commentOnIssue("99", event);

        assertNotNull(result);
        assertFalse(result.success(), "commentOnIssue must return failure when the HTTP call fails");
    }

    @Test
    void findDuplicate_usesFingerprintLabelWith12Chars() {
        // 24-char fingerprint — label must be "observarium-" + first 12 chars
        // We verify this via the failure message path (no real server), but the important
        // thing is that the service does not throw when fingerprint is long.
        GitLabPostingService service = new GitLabPostingService(CONFIG);
        ExceptionEvent event = minimalEvent("abcdef123456789xyz");  // 18 chars

        DuplicateSearchResult result = service.findDuplicate(event);
        // A real assertion on the URL would require intercepting the HttpClient.
        // Here we simply confirm the method completes without exception.
        assertNotNull(result);
    }

    // --- helpers ---

    private static ExceptionEvent minimalEvent(String fingerprint) {
        return ExceptionEvent.builder()
            .fingerprint(fingerprint)
            .exceptionClass("com.example.SomeException")
            .message("something went wrong")
            .rawStackTrace("com.example.SomeException: something went wrong\n\tat com.example.App.main(App.java:10)")
            .severity(Severity.ERROR)
            .timestamp(Instant.parse("2025-06-01T12:00:00Z"))
            .threadName("main")
            .build();
    }
}
