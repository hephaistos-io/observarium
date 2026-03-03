package io.hephaistos.observarium.jira;

import com.google.gson.Gson;
import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.event.Severity;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link JiraPostingService}.
 *
 * <p>Network calls are avoided by injecting a mock {@link HttpClient}.
 *
 * <p>All HttpClient stubs use {@code doReturn(...).when(...)} to sidestep the
 * generic-type mismatch between {@code HttpResponse<String>} and the raw return
 * type that {@code HttpClient.send()} exposes to the Mockito type-checker.
 */
class JiraPostingServiceTest {

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    private static final String BASE_URL    = "https://mycompany.atlassian.net";
    private static final String USERNAME    = "dev@example.com";
    private static final String API_TOKEN   = "secret-token";
    private static final String PROJECT_KEY = "OBS";
    private static final String ISSUE_TYPE  = "Bug";

    private JiraConfig config;

    @BeforeEach
    void setUp() {
        config = new JiraConfig(BASE_URL, USERNAME, API_TOKEN, PROJECT_KEY, ISSUE_TYPE);
    }

    private ExceptionEvent buildEvent(String fingerprint) {
        return ExceptionEvent.builder()
            .fingerprint(fingerprint)
            .exceptionClass("com.example.MyException")
            .message("Something went wrong")
            .rawStackTrace("com.example.MyException: Something went wrong\n\tat com.example.Foo.bar(Foo.java:10)")
            .severity(Severity.ERROR)
            .timestamp(Instant.parse("2026-03-03T12:00:00Z"))
            .threadName("main")
            .tags(Map.of("env", "prod"))
            .build();
    }

    /**
     * Creates a mock {@link HttpResponse} that returns the given status code and body.
     * The unchecked cast is intentional — we are constructing a test double.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private HttpResponse<String> mockResponse(int statusCode, String body) {
        HttpResponse response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return (HttpResponse<String>) response;
    }

    /**
     * Creates a mock {@link HttpClient} that returns the given response for any {@code send()} call.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private HttpClient mockClient(HttpResponse<String> response) throws Exception {
        HttpClient client = mock(HttpClient.class);
        // doReturn avoids the generic type-mismatch that when().thenReturn() triggers.
        doReturn(response).when(client).send(any(HttpRequest.class), any());
        return client;
    }

    /**
     * Creates a mock {@link HttpClient} that throws the given exception for any {@code send()} call.
     */
    @SuppressWarnings("unchecked")
    private HttpClient mockClientThrowing(Exception ex) throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any())).thenThrow(ex);
        return client;
    }

    // -------------------------------------------------------------------------
    // name()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("name() returns 'jira'")
    void name_returnsJira() {
        JiraPostingService service = new JiraPostingService(config);
        assertEquals("jira", service.name());
    }

    // -------------------------------------------------------------------------
    // JiraConfig
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("JiraConfig")
    class JiraConfigTests {

        @Test
        @DisplayName("stores all fields correctly")
        void storesFields() {
            assertEquals(BASE_URL, config.baseUrl());
            assertEquals(USERNAME, config.username());
            assertEquals(API_TOKEN, config.apiToken());
            assertEquals(PROJECT_KEY, config.projectKey());
            assertEquals(ISSUE_TYPE, config.issueType());
        }

        @Test
        @DisplayName("strips trailing slash from baseUrl")
        void stripsTrailingSlash() {
            JiraConfig c = new JiraConfig(BASE_URL + "/", USERNAME, API_TOKEN, PROJECT_KEY, ISSUE_TYPE);
            assertEquals(BASE_URL, c.baseUrl(), "trailing slash must be stripped");
        }

        @Test
        @DisplayName("defaults issueType to 'Bug' when blank")
        void defaultsIssueTypeWhenBlank() {
            JiraConfig c = new JiraConfig(BASE_URL, USERNAME, API_TOKEN, PROJECT_KEY, "  ");
            assertEquals("Bug", c.issueType());
        }

        @Test
        @DisplayName("defaults issueType to 'Bug' when null")
        void defaultsIssueTypeWhenNull() {
            JiraConfig c = new JiraConfig(BASE_URL, USERNAME, API_TOKEN, PROJECT_KEY, null);
            assertEquals("Bug", c.issueType());
        }

        @Test
        @DisplayName("of() factory defaults to 'Bug' issue type")
        void ofFactoryDefaultsBug() {
            JiraConfig c = JiraConfig.of(BASE_URL, USERNAME, API_TOKEN, PROJECT_KEY);
            assertEquals("Bug", c.issueType());
        }

        @Test
        @DisplayName("throws when baseUrl is blank")
        void throwsOnBlankBaseUrl() {
            assertThrows(IllegalArgumentException.class,
                () -> new JiraConfig("", USERNAME, API_TOKEN, PROJECT_KEY, ISSUE_TYPE));
        }

        @Test
        @DisplayName("throws when username is blank")
        void throwsOnBlankUsername() {
            assertThrows(IllegalArgumentException.class,
                () -> new JiraConfig(BASE_URL, "  ", API_TOKEN, PROJECT_KEY, ISSUE_TYPE));
        }

        @Test
        @DisplayName("throws when apiToken is blank")
        void throwsOnBlankApiToken() {
            assertThrows(IllegalArgumentException.class,
                () -> new JiraConfig(BASE_URL, USERNAME, "", PROJECT_KEY, ISSUE_TYPE));
        }

        @Test
        @DisplayName("throws when projectKey is null")
        void throwsOnNullProjectKey() {
            assertThrows(IllegalArgumentException.class,
                () -> new JiraConfig(BASE_URL, USERNAME, API_TOKEN, null, ISSUE_TYPE));
        }
    }

    // -------------------------------------------------------------------------
    // Basic auth header
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("basicAuthHeader()")
    class BasicAuthTests {

        @Test
        @DisplayName("returns 'Basic <base64(username:token)>'")
        void correctFormat() {
            JiraPostingService service = new JiraPostingService(config);
            String header = service.basicAuthHeader();

            String expectedEncoded = Base64.getEncoder()
                .encodeToString((USERNAME + ":" + API_TOKEN).getBytes(StandardCharsets.UTF_8));
            assertEquals("Basic " + expectedEncoded, header);
        }

        @Test
        @DisplayName("credentials are properly Base64 encoded and decodable")
        void base64Decodable() {
            JiraPostingService service = new JiraPostingService(config);
            String header = service.basicAuthHeader();

            assertTrue(header.startsWith("Basic "), "must start with 'Basic '");
            String encoded = header.substring("Basic ".length());
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            assertEquals(USERNAME + ":" + API_TOKEN, decoded);
        }
    }

    // -------------------------------------------------------------------------
    // fingerprintLabel()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("fingerprintLabel()")
    class FingerprintLabelTests {

        @Test
        @DisplayName("uses first 12 chars of fingerprint as label")
        void uses12Chars() {
            String fp = "abc123def456xyz789";
            String label = JiraPostingService.fingerprintLabel(fp);
            assertEquals("observarium-abc123def456", label);
        }

        @Test
        @DisplayName("uses full fingerprint when shorter than 12 chars")
        void usesFullWhenShort() {
            String fp = "short";
            String label = JiraPostingService.fingerprintLabel(fp);
            assertEquals("observarium-short", label);
        }

        @Test
        @DisplayName("uses exactly 12 chars when fingerprint is exactly 12 chars long")
        void usesAllWhenExact() {
            String fp = "exactly12chr";
            assertEquals(12, fp.length(), "precondition: fingerprint is exactly 12 chars");
            String label = JiraPostingService.fingerprintLabel(fp);
            assertEquals("observarium-exactly12chr", label);
        }

        @Test
        @DisplayName("label always starts with 'observarium-'")
        void alwaysHasPrefix() {
            assertTrue(JiraPostingService.fingerprintLabel("anyfingerprint").startsWith("observarium-"));
        }
    }

    // -------------------------------------------------------------------------
    // findDuplicate()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findDuplicate()")
    class FindDuplicateTests {

        @Test
        @DisplayName("returns found result when Jira returns a matching issue")
        void returnsFoundWhenIssueExists() throws Exception {
            ExceptionEvent event = buildEvent("abc123def456xyz789");

            String jiraResponse = """
                {
                  "issues": [
                    {
                      "key": "OBS-42",
                      "self": "https://mycompany.atlassian.net/rest/api/3/issue/OBS-42",
                      "fields": {
                        "summary": "[Observarium] MyException: Something went wrong",
                        "status": {"name": "Open"}
                      }
                    }
                  ],
                  "total": 1
                }
                """;

            JiraPostingService service = new JiraPostingService(
                config, mockClient(mockResponse(200, jiraResponse)), new Gson());
            DuplicateSearchResult result = service.findDuplicate(event);

            assertTrue(result.found());
            assertEquals("OBS-42", result.externalIssueId());
            assertEquals(BASE_URL + "/browse/OBS-42", result.url());
        }

        @Test
        @DisplayName("returns notFound when Jira returns empty issues array")
        void returnsNotFoundWhenEmpty() throws Exception {
            ExceptionEvent event = buildEvent("abc123def456xyz789");

            JiraPostingService service = new JiraPostingService(
                config, mockClient(mockResponse(200, "{\"issues\":[],\"total\":0}")), new Gson());
            DuplicateSearchResult result = service.findDuplicate(event);

            assertFalse(result.found());
            assertNull(result.externalIssueId());
        }

        @Test
        @DisplayName("returns notFound gracefully when Jira returns HTTP 401")
        void returnsNotFoundOnHttpError() throws Exception {
            ExceptionEvent event = buildEvent("fp-error-case");

            JiraPostingService service = new JiraPostingService(
                config,
                mockClient(mockResponse(401, "{\"errorMessages\":[\"Unauthorized\"]}")),
                new Gson());
            DuplicateSearchResult result = service.findDuplicate(event);

            assertFalse(result.found(), "HTTP error should result in notFound, not an exception");
        }

        @Test
        @DisplayName("returns notFound gracefully when network throws")
        void returnsNotFoundOnNetworkException() throws Exception {
            ExceptionEvent event = buildEvent("fp-network-error");

            JiraPostingService service = new JiraPostingService(
                config, mockClientThrowing(new RuntimeException("Connection refused")), new Gson());
            DuplicateSearchResult result = service.findDuplicate(event);

            assertFalse(result.found(), "network error should be swallowed and result in notFound");
        }
    }

    // -------------------------------------------------------------------------
    // createIssue()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("createIssue()")
    class CreateIssueTests {

        @Test
        @DisplayName("returns success with key and browse URL on HTTP 201")
        void returnsSuccessOn201() throws Exception {
            ExceptionEvent event = buildEvent("fingerprint-create-test");

            String jiraResponse = """
                {
                  "id": "10042",
                  "key": "OBS-43",
                  "self": "https://mycompany.atlassian.net/rest/api/3/issue/10042"
                }
                """;

            JiraPostingService service = new JiraPostingService(
                config, mockClient(mockResponse(201, jiraResponse)), new Gson());
            PostingResult result = service.createIssue(event);

            assertTrue(result.success());
            assertEquals("OBS-43", result.externalIssueId());
            assertEquals(BASE_URL + "/browse/OBS-43", result.url());
            assertNull(result.errorMessage());
        }

        @Test
        @DisplayName("returns failure when Jira returns HTTP 400")
        void returnsFailureOnHttpError() throws Exception {
            ExceptionEvent event = buildEvent("fp-400");

            JiraPostingService service = new JiraPostingService(
                config,
                mockClient(mockResponse(400, "{\"errors\":{\"summary\":\"Field required\"}}")),
                new Gson());
            PostingResult result = service.createIssue(event);

            assertFalse(result.success());
            assertNotNull(result.errorMessage());
            assertTrue(result.errorMessage().contains("400"));
        }

        @Test
        @DisplayName("returns failure gracefully when network throws")
        void returnsFailureOnNetworkException() throws Exception {
            ExceptionEvent event = buildEvent("fp-network");

            JiraPostingService service = new JiraPostingService(
                config, mockClientThrowing(new RuntimeException("Timeout")), new Gson());
            PostingResult result = service.createIssue(event);

            assertFalse(result.success());
            assertNotNull(result.errorMessage());
        }
    }

    // -------------------------------------------------------------------------
    // commentOnIssue()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("commentOnIssue()")
    class CommentOnIssueTests {

        @Test
        @DisplayName("returns success when Jira accepts the comment (HTTP 201)")
        void returnsSuccessOn201() throws Exception {
            ExceptionEvent event = buildEvent("fp-comment");

            String jiraResponse = """
                {
                  "id": "20001",
                  "self": "https://mycompany.atlassian.net/rest/api/3/issue/OBS-10/comment/20001"
                }
                """;

            JiraPostingService service = new JiraPostingService(
                config, mockClient(mockResponse(201, jiraResponse)), new Gson());
            PostingResult result = service.commentOnIssue("OBS-10", event);

            assertTrue(result.success());
            assertEquals("OBS-10", result.externalIssueId());
            assertEquals(BASE_URL + "/browse/OBS-10", result.url());
        }

        @Test
        @DisplayName("returns failure when Jira returns HTTP 404")
        void returnsFailureOn404() throws Exception {
            ExceptionEvent event = buildEvent("fp-404");

            JiraPostingService service = new JiraPostingService(
                config,
                mockClient(mockResponse(404, "{\"errorMessages\":[\"Issue Does Not Exist\"]}")),
                new Gson());
            PostingResult result = service.commentOnIssue("OBS-999", event);

            assertFalse(result.success());
            assertNotNull(result.errorMessage());
            assertTrue(result.errorMessage().contains("404"));
        }

        @Test
        @DisplayName("returns failure gracefully when network throws")
        void returnsFailureOnNetworkException() throws Exception {
            ExceptionEvent event = buildEvent("fp-net-comment");

            JiraPostingService service = new JiraPostingService(
                config, mockClientThrowing(new RuntimeException("DNS failure")), new Gson());
            PostingResult result = service.commentOnIssue("OBS-5", event);

            assertFalse(result.success());
            assertNotNull(result.errorMessage());
        }
    }
}
