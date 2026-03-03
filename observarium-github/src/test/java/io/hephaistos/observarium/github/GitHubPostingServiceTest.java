package io.hephaistos.observarium.github;

import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.event.Severity;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.IssueFormatter;
import io.hephaistos.observarium.posting.PostingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GitHubPostingService} and {@link GitHubConfig}.
 *
 * <p>HTTP interactions are verified through a stubbed {@link HttpClient} to avoid
 * any real network calls.
 */
class GitHubPostingServiceTest {

    // -------------------------------------------------------------------------
    // GitHubConfig tests
    // -------------------------------------------------------------------------

    @Test
    void config_storesAllFields() {
        GitHubConfig config = new GitHubConfig("tok", "owner", "repo", "mylabel");
        assertEquals("tok", config.token());
        assertEquals("owner", config.owner());
        assertEquals("repo", config.repo());
        assertEquals("mylabel", config.labelPrefix());
    }

    @Test
    void config_defaultsLabelPrefixWhenNull() {
        GitHubConfig config = new GitHubConfig("tok", "owner", "repo", null);
        assertEquals("observarium", config.labelPrefix());
    }

    @Test
    void config_defaultsLabelPrefixWhenBlank() {
        GitHubConfig config = new GitHubConfig("tok", "owner", "repo", "   ");
        assertEquals("observarium", config.labelPrefix());
    }

    @Test
    void config_convenienceFactory_usesDefaultLabel() {
        GitHubConfig config = GitHubConfig.of("tok", "owner", "repo");
        assertEquals("observarium", config.labelPrefix());
    }

    @Test
    void config_rejectsNullToken() {
        assertThrows(IllegalArgumentException.class,
                () -> new GitHubConfig(null, "owner", "repo", "observarium"));
    }

    @Test
    void config_rejectsBlankToken() {
        assertThrows(IllegalArgumentException.class,
                () -> new GitHubConfig("   ", "owner", "repo", "observarium"));
    }

    @Test
    void config_rejectsNullOwner() {
        assertThrows(IllegalArgumentException.class,
                () -> new GitHubConfig("tok", null, "repo", "observarium"));
    }

    @Test
    void config_rejectsNullRepo() {
        assertThrows(IllegalArgumentException.class,
                () -> new GitHubConfig("tok", "owner", null, "observarium"));
    }

    // -------------------------------------------------------------------------
    // GitHubPostingService.name() test
    // -------------------------------------------------------------------------

    @Test
    void name_returnsGithub() {
        GitHubPostingService service = new GitHubPostingService(GitHubConfig.of("tok", "owner", "repo"));
        assertEquals("github", service.name());
    }

    // -------------------------------------------------------------------------
    // IssueFormatter delegation tests — verify the right content is sent
    // -------------------------------------------------------------------------

    @Test
    void issueFormatter_title_stripsDotSeparatedPackageName() {
        ExceptionEvent event = buildEvent("com.example.MyException", "something went wrong");
        String title = IssueFormatter.title(event);
        assertEquals("[Observarium] MyException: something went wrong", title);
    }

    @Test
    void issueFormatter_title_truncatesLongMessage() {
        String longMessage = "a".repeat(100);
        ExceptionEvent event = buildEvent("MyException", longMessage);
        String title = IssueFormatter.title(event);
        assertTrue(title.endsWith("..."));
        // Total after prefix "[Observarium] MyException: " is 28 chars + 80-char title segment
        assertTrue(title.length() <= "[Observarium] MyException: ".length() + 80);
    }

    @Test
    void issueFormatter_title_handlesNullMessage() {
        ExceptionEvent event = ExceptionEvent.builder()
                .fingerprint("fp1")
                .exceptionClass("MyException")
                .rawStackTrace("stack")
                .threadName("main")
                .build();
        String title = IssueFormatter.title(event);
        assertEquals("[Observarium] MyException: (no message)", title);
    }

    @Test
    void issueFormatter_markdownBody_containsFingerprintMarker() {
        ExceptionEvent event = buildEvent("MyException", "oops");
        String body = IssueFormatter.markdownBody(event);
        assertTrue(body.contains("<!-- observarium:fingerprint:test-fingerprint -->"),
                "Body must contain the fingerprint HTML comment for duplicate detection");
    }

    @Test
    void issueFormatter_markdownBody_containsStackTrace() {
        ExceptionEvent event = buildEvent("MyException", "oops");
        String body = IssueFormatter.markdownBody(event);
        assertTrue(body.contains("at com.example.Foo.bar(Foo.java:42)"));
    }

    @Test
    void issueFormatter_markdownComment_containsTimestamp() {
        ExceptionEvent event = buildEvent("MyException", "oops");
        String comment = IssueFormatter.markdownComment(event);
        assertTrue(comment.contains("## Occurred Again"));
        assertTrue(comment.contains("Timestamp"));
    }

    @Test
    void issueFormatter_fingerprintMarker_format() {
        String marker = IssueFormatter.fingerprintMarker("abc123");
        assertEquals("<!-- observarium:fingerprint:abc123 -->", marker);
    }

    // -------------------------------------------------------------------------
    // HTTP interaction tests using a stubbed HttpClient
    // -------------------------------------------------------------------------

    private HttpClient mockHttpClient;

    @BeforeEach
    void setUpMockClient() {
        mockHttpClient = mock(HttpClient.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findDuplicate_returnFound_whenIssueExistsWithLabel() throws Exception {
        String listResponse = """
                [
                  { "number": 42, "html_url": "https://github.com/owner/repo/issues/42" }
                ]
                """;

        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(listResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        GitHubPostingService service = new GitHubPostingService(
                GitHubConfig.of("tok", "owner", "repo"), mockHttpClient);

        DuplicateSearchResult result = service.findDuplicate(buildEvent("MyException", "oops"));

        assertTrue(result.found());
        assertEquals("42", result.externalIssueId());
        assertEquals("https://github.com/owner/repo/issues/42", result.url());
    }

    @Test
    @SuppressWarnings("unchecked")
    void findDuplicate_returnsNotFound_whenNoIssuesWithLabel() throws Exception {
        String listResponse = "[]";

        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(listResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        GitHubPostingService service = new GitHubPostingService(
                GitHubConfig.of("tok", "owner", "repo"), mockHttpClient);

        DuplicateSearchResult result = service.findDuplicate(buildEvent("MyException", "oops"));

        assertFalse(result.found());
        assertNull(result.externalIssueId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void findDuplicate_returnsNotFound_whenGitHubReturnsError() throws Exception {
        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(422);
        when(httpResponse.body()).thenReturn("{\"message\":\"Validation Failed\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        GitHubPostingService service = new GitHubPostingService(
                GitHubConfig.of("tok", "owner", "repo"), mockHttpClient);

        DuplicateSearchResult result = service.findDuplicate(buildEvent("MyException", "oops"));

        assertFalse(result.found());
    }

    @Test
    @SuppressWarnings("unchecked")
    void findDuplicate_returnsNotFound_onNetworkFailure() throws Exception {
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("connection refused"));

        GitHubPostingService service = new GitHubPostingService(
                GitHubConfig.of("tok", "owner", "repo"), mockHttpClient);

        DuplicateSearchResult result = service.findDuplicate(buildEvent("MyException", "oops"));

        assertFalse(result.found());
    }

    @Test
    @SuppressWarnings("unchecked")
    void createIssue_returnsSuccess_whenGitHubResponds201() throws Exception {
        String createResponse = """
                {
                  "number": 7,
                  "html_url": "https://github.com/owner/repo/issues/7"
                }
                """;

        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(201);
        when(httpResponse.body()).thenReturn(createResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        GitHubPostingService service = new GitHubPostingService(
                GitHubConfig.of("tok", "owner", "repo"), mockHttpClient);

        PostingResult result = service.createIssue(buildEvent("MyException", "oops"));

        assertTrue(result.success());
        assertEquals("7", result.externalIssueId());
        assertEquals("https://github.com/owner/repo/issues/7", result.url());
        assertNull(result.errorMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void createIssue_returnsFailure_whenGitHubReturns403() throws Exception {
        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(403);
        when(httpResponse.body()).thenReturn("{\"message\":\"Forbidden\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        GitHubPostingService service = new GitHubPostingService(
                GitHubConfig.of("tok", "owner", "repo"), mockHttpClient);

        PostingResult result = service.createIssue(buildEvent("MyException", "oops"));

        assertFalse(result.success());
        assertNotNull(result.errorMessage());
        assertTrue(result.errorMessage().contains("403"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createIssue_returnsFailure_onNetworkError() throws Exception {
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("timeout"));

        GitHubPostingService service = new GitHubPostingService(
                GitHubConfig.of("tok", "owner", "repo"), mockHttpClient);

        PostingResult result = service.createIssue(buildEvent("MyException", "oops"));

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("timeout"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void commentOnIssue_returnsSuccess_withCommentUrl() throws Exception {
        String commentResponse = """
                {
                  "id": 999,
                  "html_url": "https://github.com/owner/repo/issues/7#issuecomment-999"
                }
                """;

        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(201);
        when(httpResponse.body()).thenReturn(commentResponse);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        GitHubPostingService service = new GitHubPostingService(
                GitHubConfig.of("tok", "owner", "repo"), mockHttpClient);

        PostingResult result = service.commentOnIssue("7", buildEvent("MyException", "oops"));

        assertTrue(result.success());
        assertEquals("7", result.externalIssueId());
        assertEquals("https://github.com/owner/repo/issues/7#issuecomment-999", result.url());
    }

    @Test
    @SuppressWarnings("unchecked")
    void commentOnIssue_returnsFailure_whenGitHubReturns404() throws Exception {
        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(404);
        when(httpResponse.body()).thenReturn("{\"message\":\"Not Found\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        GitHubPostingService service = new GitHubPostingService(
                GitHubConfig.of("tok", "owner", "repo"), mockHttpClient);

        PostingResult result = service.commentOnIssue("9999", buildEvent("MyException", "oops"));

        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("404"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ExceptionEvent buildEvent(String exceptionClass, String message) {
        return ExceptionEvent.builder()
                .fingerprint("test-fingerprint")
                .exceptionClass(exceptionClass)
                .message(message)
                .rawStackTrace("at com.example.Foo.bar(Foo.java:42)")
                .severity(Severity.ERROR)
                .timestamp(Instant.parse("2024-01-15T10:30:00Z"))
                .threadName("main")
                .tags(Map.of("env", "test"))
                .build();
    }
}
