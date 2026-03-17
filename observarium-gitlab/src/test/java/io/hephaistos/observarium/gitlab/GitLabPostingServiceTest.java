package io.hephaistos.observarium.gitlab;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.event.Severity;
import io.hephaistos.observarium.posting.DefaultIssueFormatter;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GitLabPostingService} and {@link GitLabConfig}.
 *
 * <p>HTTP interactions are verified through a stubbed {@link HttpClient} to avoid any real network
 * calls.
 */
class GitLabPostingServiceTest {

  private static final GitLabConfig CONFIG =
      new GitLabConfig("https://gitlab.example.com", "glpat-test-token", "42");

  private static final GitLabConfig NAMESPACE_CONFIG =
      new GitLabConfig("https://gitlab.example.com", "glpat-test-token", "mygroup/myproject");

  private HttpClient mockHttpClient;

  @BeforeEach
  void setUpMockClient() {
    mockHttpClient = mock(HttpClient.class);
  }

  // -------------------------------------------------------------------------
  // GitLabConfig tests
  // -------------------------------------------------------------------------

  @Test
  void config_storesAllFields() {
    assertEquals("https://gitlab.example.com", CONFIG.baseUrl());
    assertEquals("glpat-test-token", CONFIG.privateToken());
    assertEquals("42", CONFIG.projectId());
  }

  // -------------------------------------------------------------------------
  // name()
  // -------------------------------------------------------------------------

  @Test
  void name_returnsGitlab() {
    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    assertEquals("gitlab", service.name());
  }

  // -------------------------------------------------------------------------
  // findDuplicate() — success paths
  // -------------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void findDuplicate_returnsFound_whenIssueExists() throws Exception {
    String responseBody =
        """
                [
                  { "iid": 5, "web_url": "https://gitlab.example.com/group/proj/-/issues/5" }
                ]
                """;

    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(responseBody);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    DuplicateSearchResult result = service.findDuplicate(buildEvent("SomeException", "boom"));

    assertTrue(result.found());
    assertEquals("5", result.externalIssueId());
    assertEquals("https://gitlab.example.com/group/proj/-/issues/5", result.url());
  }

  @Test
  @SuppressWarnings("unchecked")
  void findDuplicate_returnsFound_usesFirstIssueWhenMultipleExist() throws Exception {
    String responseBody =
        """
                [
                  { "iid": 3, "web_url": "https://gitlab.example.com/proj/-/issues/3" },
                  { "iid": 7, "web_url": "https://gitlab.example.com/proj/-/issues/7" }
                ]
                """;

    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(responseBody);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    DuplicateSearchResult result = service.findDuplicate(buildEvent("SomeException", "boom"));

    assertTrue(result.found());
    assertEquals("3", result.externalIssueId());
  }

  @Test
  @SuppressWarnings("unchecked")
  void findDuplicate_returnsNotFound_whenResponseIsEmptyArray() throws Exception {
    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn("[]");
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    DuplicateSearchResult result = service.findDuplicate(buildEvent("SomeException", "boom"));

    assertFalse(result.found());
    assertNull(result.externalIssueId());
  }

  // -------------------------------------------------------------------------
  // findDuplicate() — error paths
  // -------------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void findDuplicate_returnsNotFound_whenGitLabReturns401() throws Exception {
    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(401);
    when(httpResponse.body()).thenReturn("{\"message\":\"401 Unauthorized\"}");
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    DuplicateSearchResult result = service.findDuplicate(buildEvent("SomeException", "boom"));

    assertFalse(result.found());
  }

  @Test
  @SuppressWarnings("unchecked")
  void findDuplicate_returnsNotFound_whenGitLabReturns500() throws Exception {
    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(500);
    when(httpResponse.body()).thenReturn("Internal Server Error");
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    DuplicateSearchResult result = service.findDuplicate(buildEvent("SomeException", "boom"));

    assertFalse(result.found());
  }

  @Test
  @SuppressWarnings("unchecked")
  void findDuplicate_returnsNotFound_onNetworkFailure() throws Exception {
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IOException("connection refused"));

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    DuplicateSearchResult result = service.findDuplicate(buildEvent("SomeException", "boom"));

    assertFalse(result.found());
  }

  @Test
  @SuppressWarnings("unchecked")
  void findDuplicate_returnsNotFound_onInterruptedException() throws Exception {
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new InterruptedException("interrupted"));

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    DuplicateSearchResult result = service.findDuplicate(buildEvent("SomeException", "boom"));

    assertFalse(result.found());
    assertTrue(Thread.interrupted(), "Service must restore the interrupt flag");
  }

  // -------------------------------------------------------------------------
  // createIssue() — success paths
  // -------------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void createIssue_returnsSuccess_whenGitLabResponds201() throws Exception {
    String responseBody =
        """
                {
                  "iid": 12,
                  "web_url": "https://gitlab.example.com/group/proj/-/issues/12"
                }
                """;

    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(201);
    when(httpResponse.body()).thenReturn(responseBody);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    PostingResult result = service.createIssue(buildEvent("SomeException", "boom"));

    assertTrue(result.success());
    assertEquals("12", result.externalIssueId());
    assertEquals("https://gitlab.example.com/group/proj/-/issues/12", result.url());
    assertNull(result.errorMessage());
  }

  @Test
  @SuppressWarnings("unchecked")
  void createIssue_returnsSuccess_whenGitLabResponds200() throws Exception {
    String responseBody =
        """
                {
                  "iid": 7,
                  "web_url": "https://gitlab.example.com/proj/-/issues/7"
                }
                """;

    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(responseBody);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    PostingResult result = service.createIssue(buildEvent("SomeException", "boom"));

    assertTrue(result.success());
    assertEquals("7", result.externalIssueId());
  }

  // -------------------------------------------------------------------------
  // createIssue() — error paths
  // -------------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void createIssue_returnsFailure_whenGitLabReturns403() throws Exception {
    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(403);
    when(httpResponse.body()).thenReturn("{\"message\":\"403 Forbidden\"}");
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    PostingResult result = service.createIssue(buildEvent("SomeException", "boom"));

    assertFalse(result.success());
    assertNotNull(result.errorMessage());
    assertTrue(result.errorMessage().contains("403"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void createIssue_returnsFailure_whenGitLabReturns422() throws Exception {
    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(422);
    when(httpResponse.body()).thenReturn("{\"message\":\"Validation failed\"}");
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    PostingResult result = service.createIssue(buildEvent("SomeException", "boom"));

    assertFalse(result.success());
    assertTrue(result.errorMessage().contains("422"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void createIssue_returnsFailure_onNetworkError() throws Exception {
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IOException("timeout"));

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    PostingResult result = service.createIssue(buildEvent("SomeException", "boom"));

    assertFalse(result.success());
    assertNotNull(result.errorMessage());
    assertTrue(result.errorMessage().contains("timeout"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void createIssue_returnsFailure_onInterruptedException() throws Exception {
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new InterruptedException("interrupted"));

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    PostingResult result = service.createIssue(buildEvent("SomeException", "boom"));

    assertFalse(result.success());
    assertNotNull(result.errorMessage());
    assertTrue(Thread.interrupted(), "Service must restore the interrupt flag");
  }

  // -------------------------------------------------------------------------
  // commentOnIssue() — success paths
  // -------------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void commentOnIssue_returnsSuccess_withIssueId() throws Exception {
    String responseBody =
        """
                {
                  "id": 888,
                  "body": "Occurred again"
                }
                """;

    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(201);
    when(httpResponse.body()).thenReturn(responseBody);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    PostingResult result = service.commentOnIssue("5", buildEvent("SomeException", "boom"));

    assertTrue(result.success());
    // commentOnIssue returns the issue IID that was passed in, consistent with GitHub and Jira
    assertEquals("5", result.externalIssueId());
    assertNull(result.url());
  }

  @Test
  @SuppressWarnings("unchecked")
  void commentOnIssue_returnsSuccess_whenGitLabResponds200() throws Exception {
    String responseBody =
        """
                {
                  "id": 42,
                  "body": "comment text"
                }
                """;

    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(responseBody);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    PostingResult result = service.commentOnIssue("10", buildEvent("SomeException", "boom"));

    assertTrue(result.success());
    // commentOnIssue returns the issue IID that was passed in, consistent with GitHub and Jira
    assertEquals("10", result.externalIssueId());
  }

  // -------------------------------------------------------------------------
  // commentOnIssue() — error paths
  // -------------------------------------------------------------------------

  @Test
  void commentOnIssue_throwsNullPointerException_whenExternalIssueIdIsNull() {
    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    ExceptionEvent event = buildEvent("SomeException", "boom");
    assertThrows(NullPointerException.class, () -> service.commentOnIssue(null, event));
  }

  @Test
  @SuppressWarnings("unchecked")
  void commentOnIssue_returnsFailure_whenGitLabReturns404() throws Exception {
    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(404);
    when(httpResponse.body()).thenReturn("{\"message\":\"404 Issue Not Found\"}");
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    PostingResult result = service.commentOnIssue("9999", buildEvent("SomeException", "boom"));

    assertFalse(result.success());
    assertNotNull(result.errorMessage());
    assertTrue(result.errorMessage().contains("404"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void commentOnIssue_returnsFailure_whenGitLabReturns403() throws Exception {
    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(403);
    when(httpResponse.body()).thenReturn("{\"message\":\"Forbidden\"}");
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    PostingResult result = service.commentOnIssue("1", buildEvent("SomeException", "boom"));

    assertFalse(result.success());
    assertTrue(result.errorMessage().contains("403"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void commentOnIssue_returnsFailure_onNetworkFailure() throws Exception {
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IOException("connection reset"));

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    PostingResult result = service.commentOnIssue("5", buildEvent("SomeException", "boom"));

    assertFalse(result.success());
    assertNotNull(result.errorMessage());
    assertTrue(result.errorMessage().contains("connection reset"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void commentOnIssue_returnsFailure_onInterruptedException() throws Exception {
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new InterruptedException("interrupted"));

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    PostingResult result = service.commentOnIssue("5", buildEvent("SomeException", "boom"));

    assertFalse(result.success());
    assertNotNull(result.errorMessage());
    assertTrue(Thread.interrupted(), "Service must restore the interrupt flag");
  }

  // -------------------------------------------------------------------------
  // findDuplicate() — comment count
  // -------------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void findDuplicate_returnsKnownCommentCount_whenIssueHasUserNotesCountField() throws Exception {
    String responseBody =
        """
                [
                  {
                    "iid": 5,
                    "web_url": "https://gitlab.example.com/group/proj/-/issues/5",
                    "user_notes_count": 7
                  }
                ]
                """;

    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(responseBody);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    DuplicateSearchResult result = service.findDuplicate(buildEvent("SomeException", "boom"));

    assertTrue(result.found());
    assertEquals(7, result.commentCount());
  }

  @Test
  @SuppressWarnings("unchecked")
  void findDuplicate_returnsUnknownCommentCount_whenIssueHasNoUserNotesCountField()
      throws Exception {
    // GitLab response without the "user_notes_count" field
    String responseBody =
        """
                [
                  { "iid": 5, "web_url": "https://gitlab.example.com/group/proj/-/issues/5" }
                ]
                """;

    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(responseBody);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    DuplicateSearchResult result = service.findDuplicate(buildEvent("SomeException", "boom"));

    assertTrue(result.found());
    assertEquals(DuplicateSearchResult.COMMENT_COUNT_UNKNOWN, result.commentCount());
  }

  // -------------------------------------------------------------------------
  // postCommentLimitNotice() — success paths
  // -------------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void postCommentLimitNotice_returnsSuccess_withIssueId() throws Exception {
    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(201);
    when(httpResponse.body()).thenReturn("{\"id\": 999, \"body\": \"comment limit notice\"}");
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    PostingResult result = service.postCommentLimitNotice("5", 10);

    assertTrue(result.success());
    assertEquals("5", result.externalIssueId());
    assertNull(result.url());
  }

  // -------------------------------------------------------------------------
  // postCommentLimitNotice() — error paths
  // -------------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void postCommentLimitNotice_returnsFailure_whenGitLabReturns404() throws Exception {
    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(404);
    when(httpResponse.body()).thenReturn("{\"message\":\"404 Issue Not Found\"}");
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    PostingResult result = service.postCommentLimitNotice("9999", 10);

    assertFalse(result.success());
    assertNotNull(result.errorMessage());
    assertTrue(result.errorMessage().contains("404"));
  }

  @Test
  void postCommentLimitNotice_throwsNullPointerException_whenExternalIssueIdIsNull() {
    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    assertThrows(NullPointerException.class, () -> service.postCommentLimitNotice(null, 10));
  }

  // -------------------------------------------------------------------------
  // URL encoding — namespace/path project IDs
  // -------------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void findDuplicate_urlEncodesNamespaceProjectId() throws Exception {
    // "mygroup/myproject" must be percent-encoded to "mygroup%2Fmyproject" in the URL
    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn("[]");
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(
            NAMESPACE_CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    service.findDuplicate(buildEvent("SomeException", "boom"));

    verify(mockHttpClient)
        .send(
            argThat(req -> req.uri().toString().contains("mygroup%2Fmyproject")),
            any(HttpResponse.BodyHandler.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void createIssue_urlEncodesNamespaceProjectId() throws Exception {
    String responseBody =
        """
                { "iid": 1, "web_url": "https://gitlab.example.com/mygroup/myproject/-/issues/1" }
                """;

    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(201);
    when(httpResponse.body()).thenReturn(responseBody);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(
            NAMESPACE_CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    service.createIssue(buildEvent("SomeException", "boom"));

    verify(mockHttpClient)
        .send(
            argThat(req -> req.uri().toString().contains("mygroup%2Fmyproject")),
            any(HttpResponse.BodyHandler.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void commentOnIssue_urlEncodesNamespaceProjectId() throws Exception {
    String responseBody = "{ \"id\": 1 }";

    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(201);
    when(httpResponse.body()).thenReturn(responseBody);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(
            NAMESPACE_CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    service.commentOnIssue("3", buildEvent("SomeException", "boom"));

    verify(mockHttpClient)
        .send(
            argThat(req -> req.uri().toString().contains("mygroup%2Fmyproject")),
            any(HttpResponse.BodyHandler.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void postCommentLimitNotice_urlEncodesNamespaceProjectId() throws Exception {
    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(201);
    when(httpResponse.body()).thenReturn("{\"id\": 1}");
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(
            NAMESPACE_CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    service.postCommentLimitNotice("3", 10);

    verify(mockHttpClient)
        .send(
            argThat(req -> req.uri().toString().contains("mygroup%2Fmyproject")),
            any(HttpResponse.BodyHandler.class));
  }

  // -------------------------------------------------------------------------
  // fingerprintLabel truncation edge cases
  // -------------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void findDuplicate_truncatesFingerprintLabelToFirst12Chars() throws Exception {
    // fingerprint is 24 chars; label must use only the first 12
    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn("[]");
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    service.findDuplicate(buildEvent("SomeException", "boom", "abcdef123456789xyz012345"));

    verify(mockHttpClient)
        .send(
            argThat(req -> req.uri().toString().contains("observarium-abcdef123456")),
            any(HttpResponse.BodyHandler.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void findDuplicate_usesFullFingerprintWhenShorterThan12Chars() throws Exception {
    // fingerprint is only 6 chars; label must use it in full
    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn("[]");
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    service.findDuplicate(buildEvent("SomeException", "boom", "short"));

    verify(mockHttpClient)
        .send(
            argThat(req -> req.uri().toString().contains("observarium-short")),
            any(HttpResponse.BodyHandler.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  void findDuplicate_usesFullFingerprintWhenExactly12Chars() throws Exception {
    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn("[]");
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitLabPostingService service =
        new GitLabPostingService(CONFIG, mockHttpClient, new Gson(), new DefaultIssueFormatter());
    service.findDuplicate(buildEvent("SomeException", "boom", "exactly12chr"));

    verify(mockHttpClient)
        .send(
            argThat(req -> req.uri().toString().contains("observarium-exactly12chr")),
            any(HttpResponse.BodyHandler.class));
  }

  // -------------------------------------------------------------------------
  // Fallback: network-failure tests using the real constructor
  // (exercises the public single-arg constructor code path)
  // -------------------------------------------------------------------------

  @Test
  void findDuplicate_returnsNotFound_whenRealHttpClientCannotConnect() {
    GitLabPostingService service = new GitLabPostingService(CONFIG);
    DuplicateSearchResult result = service.findDuplicate(buildEvent("SomeException", "boom"));
    assertFalse(result.found());
  }

  @Test
  void createIssue_returnsFailure_whenRealHttpClientCannotConnect() {
    GitLabPostingService service = new GitLabPostingService(CONFIG);
    PostingResult result = service.createIssue(buildEvent("SomeException", "boom"));
    assertFalse(result.success());
  }

  @Test
  void commentOnIssue_returnsFailure_whenRealHttpClientCannotConnect() {
    GitLabPostingService service = new GitLabPostingService(CONFIG);
    PostingResult result = service.commentOnIssue("99", buildEvent("SomeException", "boom"));
    assertFalse(result.success());
  }

  @Test
  void postCommentLimitNotice_returnsFailure_whenRealHttpClientCannotConnect() {
    GitLabPostingService service = new GitLabPostingService(CONFIG);
    PostingResult result = service.postCommentLimitNotice("99", 10);
    assertFalse(result.success());
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private ExceptionEvent buildEvent(String exceptionClass, String message) {
    return buildEvent(exceptionClass, message, "test-fingerprint");
  }

  private ExceptionEvent buildEvent(String exceptionClass, String message, String fingerprint) {
    return ExceptionEvent.builder()
        .fingerprint(fingerprint)
        .exceptionClass(exceptionClass)
        .message(message)
        .rawStackTrace("at com.example.Foo.bar(Foo.java:42)")
        .severity(Severity.ERROR)
        .timestamp(Instant.parse("2025-06-01T12:00:00Z"))
        .threadName("main")
        .tags(Map.of("env", "test"))
        .build();
  }
}
