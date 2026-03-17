package io.hephaistos.observarium.github;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.event.Severity;
import io.hephaistos.observarium.posting.DefaultIssueFormatter;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.IssueFormatter;
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
 * Unit tests for {@link GitHubPostingService} and {@link GitHubConfig}.
 *
 * <p>HTTP interactions are verified through a stubbed {@link HttpClient} to avoid any real network
 * calls.
 */
class GitHubPostingServiceTest {

  // -------------------------------------------------------------------------
  // GitHubConfig tests
  // -------------------------------------------------------------------------

  @Test
  void config_storesAllFields() {
    GitHubConfig config = new GitHubConfig("tok", "owner", "repo", "mylabel", null);
    assertEquals("tok", config.token());
    assertEquals("owner", config.owner());
    assertEquals("repo", config.repo());
    assertEquals("mylabel", config.labelPrefix());
    assertEquals("https://api.github.com", config.baseUrl());
  }

  @Test
  void config_defaultsBaseUrlWhenNull() {
    GitHubConfig config = new GitHubConfig("tok", "owner", "repo", null, null);
    assertEquals("https://api.github.com", config.baseUrl());
  }

  @Test
  void config_usesCustomBaseUrl() {
    GitHubConfig config =
        new GitHubConfig("tok", "owner", "repo", null, "https://ghe.example.com/api/v3");
    assertEquals("https://ghe.example.com/api/v3", config.baseUrl());
  }

  @Test
  void config_defaultsLabelPrefixWhenNull() {
    GitHubConfig config = new GitHubConfig("tok", "owner", "repo", null, null);
    assertEquals("observarium", config.labelPrefix());
  }

  @Test
  void config_defaultsLabelPrefixWhenBlank() {
    GitHubConfig config = new GitHubConfig("tok", "owner", "repo", "   ", null);
    assertEquals("observarium", config.labelPrefix());
  }

  @Test
  void config_convenienceFactory_usesDefaultLabel() {
    GitHubConfig config = GitHubConfig.of("tok", "owner", "repo");
    assertEquals("observarium", config.labelPrefix());
    assertEquals("https://api.github.com", config.baseUrl());
  }

  @Test
  void config_rejectsNullToken() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new GitHubConfig(null, "owner", "repo", "observarium", null));
  }

  @Test
  void config_rejectsBlankToken() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new GitHubConfig("   ", "owner", "repo", "observarium", null));
  }

  @Test
  void config_rejectsNullOwner() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new GitHubConfig("tok", null, "repo", "observarium", null));
  }

  @Test
  void config_rejectsBlankOwner() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new GitHubConfig("tok", "   ", "repo", "observarium", null));
  }

  @Test
  void config_rejectsNullRepo() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new GitHubConfig("tok", "owner", null, "observarium", null));
  }

  @Test
  void config_rejectsBlankRepo() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new GitHubConfig("tok", "owner", "   ", "observarium", null));
  }

  @Test
  void config_stripsTrailingSlashFromBaseUrl() {
    GitHubConfig config =
        new GitHubConfig("tok", "owner", "repo", null, "https://ghe.example.com/api/v3/");
    assertEquals("https://ghe.example.com/api/v3", config.baseUrl());
  }

  @Test
  void config_stripsMultipleTrailingSlashesFromBaseUrl() {
    GitHubConfig config =
        new GitHubConfig("tok", "owner", "repo", null, "https://ghe.example.com/api/v3///");
    assertEquals("https://ghe.example.com/api/v3", config.baseUrl());
  }

  // -------------------------------------------------------------------------
  // GitHubPostingService.name() test
  // -------------------------------------------------------------------------

  @Test
  void name_returnsGithub() {
    GitHubPostingService service =
        new GitHubPostingService(GitHubConfig.of("tok", "owner", "repo"));
    assertEquals("github", service.name());
  }

  @Test
  void twoArgConstructor_usesDefaultHttpClient() {
    IssueFormatter customFormatter = mock(IssueFormatter.class);
    when(customFormatter.title(any())).thenReturn("title");
    when(customFormatter.markdownBody(any())).thenReturn("body");
    GitHubPostingService service =
        new GitHubPostingService(GitHubConfig.of("tok", "owner", "repo"), customFormatter);
    assertEquals("github", service.name());
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
    String listResponse =
        """
                [
                  { "number": 42, "html_url": "https://github.com/owner/repo/issues/42" }
                ]
                """;

    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(listResponse);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitHubPostingService service =
        new GitHubPostingService(
            GitHubConfig.of("tok", "owner", "repo"), mockHttpClient, new DefaultIssueFormatter());

    DuplicateSearchResult result = service.findDuplicate(buildEvent("MyException", "oops"));

    assertTrue(result.found());
    assertEquals("42", result.externalIssueId());
    assertEquals("https://github.com/owner/repo/issues/42", result.url());
  }

  @Test
  @SuppressWarnings("unchecked")
  void findDuplicate_returnsKnownCommentCount_whenIssueHasCommentsField() throws Exception {
    String listResponse =
        """
                [
                  {
                    "number": 42,
                    "html_url": "https://github.com/owner/repo/issues/42",
                    "comments": 7
                  }
                ]
                """;

    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(listResponse);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitHubPostingService service =
        new GitHubPostingService(
            GitHubConfig.of("tok", "owner", "repo"), mockHttpClient, new DefaultIssueFormatter());

    DuplicateSearchResult result = service.findDuplicate(buildEvent("MyException", "oops"));

    assertTrue(result.found());
    assertEquals(7, result.commentCount());
  }

  @Test
  @SuppressWarnings("unchecked")
  void findDuplicate_returnsUnknownCommentCount_whenIssueHasNoCommentsField() throws Exception {
    // GitHub response without the "comments" field
    String listResponse =
        """
                [
                  { "number": 42, "html_url": "https://github.com/owner/repo/issues/42" }
                ]
                """;

    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(200);
    when(httpResponse.body()).thenReturn(listResponse);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitHubPostingService service =
        new GitHubPostingService(
            GitHubConfig.of("tok", "owner", "repo"), mockHttpClient, new DefaultIssueFormatter());

    DuplicateSearchResult result = service.findDuplicate(buildEvent("MyException", "oops"));

    assertTrue(result.found());
    assertEquals(DuplicateSearchResult.COMMENT_COUNT_UNKNOWN, result.commentCount());
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

    GitHubPostingService service =
        new GitHubPostingService(
            GitHubConfig.of("tok", "owner", "repo"), mockHttpClient, new DefaultIssueFormatter());

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

    GitHubPostingService service =
        new GitHubPostingService(
            GitHubConfig.of("tok", "owner", "repo"), mockHttpClient, new DefaultIssueFormatter());

    DuplicateSearchResult result = service.findDuplicate(buildEvent("MyException", "oops"));

    assertFalse(result.found());
  }

  @Test
  @SuppressWarnings("unchecked")
  void findDuplicate_returnsNotFound_onNetworkFailure() throws Exception {
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IOException("connection refused"));

    GitHubPostingService service =
        new GitHubPostingService(
            GitHubConfig.of("tok", "owner", "repo"), mockHttpClient, new DefaultIssueFormatter());

    DuplicateSearchResult result = service.findDuplicate(buildEvent("MyException", "oops"));

    assertFalse(result.found());
  }

  @Test
  @SuppressWarnings("unchecked")
  void createIssue_returnsSuccess_whenGitHubResponds201() throws Exception {
    String createResponse =
        """
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

    GitHubPostingService service =
        new GitHubPostingService(
            GitHubConfig.of("tok", "owner", "repo"), mockHttpClient, new DefaultIssueFormatter());

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

    GitHubPostingService service =
        new GitHubPostingService(
            GitHubConfig.of("tok", "owner", "repo"), mockHttpClient, new DefaultIssueFormatter());

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

    GitHubPostingService service =
        new GitHubPostingService(
            GitHubConfig.of("tok", "owner", "repo"), mockHttpClient, new DefaultIssueFormatter());

    PostingResult result = service.createIssue(buildEvent("MyException", "oops"));

    assertFalse(result.success());
    assertTrue(result.errorMessage().contains("timeout"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void commentOnIssue_returnsSuccess_withCommentUrl() throws Exception {
    String commentResponse =
        """
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

    GitHubPostingService service =
        new GitHubPostingService(
            GitHubConfig.of("tok", "owner", "repo"), mockHttpClient, new DefaultIssueFormatter());

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

    GitHubPostingService service =
        new GitHubPostingService(
            GitHubConfig.of("tok", "owner", "repo"), mockHttpClient, new DefaultIssueFormatter());

    PostingResult result = service.commentOnIssue("9999", buildEvent("MyException", "oops"));

    assertFalse(result.success());
    assertTrue(result.errorMessage().contains("404"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void commentOnIssue_returnsFailure_onNetworkError() throws Exception {
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IOException("connection reset"));

    GitHubPostingService service =
        new GitHubPostingService(
            GitHubConfig.of("tok", "owner", "repo"), mockHttpClient, new DefaultIssueFormatter());

    PostingResult result = service.commentOnIssue("7", buildEvent("MyException", "oops"));

    assertFalse(result.success());
    assertTrue(result.errorMessage().contains("connection reset"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void commentOnIssue_returnsFailure_onInterruptedException() throws Exception {
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new InterruptedException("interrupted"));

    GitHubPostingService service =
        new GitHubPostingService(
            GitHubConfig.of("tok", "owner", "repo"), mockHttpClient, new DefaultIssueFormatter());

    PostingResult result = service.commentOnIssue("7", buildEvent("MyException", "oops"));

    assertFalse(result.success());
    assertTrue(result.errorMessage().contains("interrupted"));
    // Clear the interrupt flag set by the service so it does not bleed into other tests
    Thread.interrupted();
  }

  @Test
  void commentOnIssue_throwsNullPointerException_whenExternalIssueIdIsNull() {
    GitHubPostingService service =
        new GitHubPostingService(
            GitHubConfig.of("tok", "owner", "repo"), mockHttpClient, new DefaultIssueFormatter());
    ExceptionEvent event = buildEvent("MyException", "oops");
    assertThrows(NullPointerException.class, () -> service.commentOnIssue(null, event));
  }

  @Test
  @SuppressWarnings("unchecked")
  void postCommentLimitNotice_returnsSuccess_withCommentUrl() throws Exception {
    String commentResponse =
        """
                {
                  "id": 888,
                  "html_url": "https://github.com/owner/repo/issues/7#issuecomment-888"
                }
                """;

    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(201);
    when(httpResponse.body()).thenReturn(commentResponse);
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitHubPostingService service =
        new GitHubPostingService(
            GitHubConfig.of("tok", "owner", "repo"), mockHttpClient, new DefaultIssueFormatter());

    PostingResult result = service.postCommentLimitNotice("7", 10);

    assertTrue(result.success());
    assertEquals("7", result.externalIssueId());
    assertEquals("https://github.com/owner/repo/issues/7#issuecomment-888", result.url());
  }

  @Test
  @SuppressWarnings("unchecked")
  void postCommentLimitNotice_returnsFailure_whenGitHubReturns404() throws Exception {
    HttpResponse<String> httpResponse = mock(HttpResponse.class);
    when(httpResponse.statusCode()).thenReturn(404);
    when(httpResponse.body()).thenReturn("{\"message\":\"Not Found\"}");
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenReturn(httpResponse);

    GitHubPostingService service =
        new GitHubPostingService(
            GitHubConfig.of("tok", "owner", "repo"), mockHttpClient, new DefaultIssueFormatter());

    PostingResult result = service.postCommentLimitNotice("9999", 10);

    assertFalse(result.success());
    assertTrue(result.errorMessage().contains("404"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void postCommentLimitNotice_returnsFailure_onNetworkError() throws Exception {
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new IOException("connection reset"));

    GitHubPostingService service =
        new GitHubPostingService(
            GitHubConfig.of("tok", "owner", "repo"), mockHttpClient, new DefaultIssueFormatter());

    PostingResult result = service.postCommentLimitNotice("7", 10);

    assertFalse(result.success());
    assertTrue(result.errorMessage().contains("connection reset"));
  }

  @Test
  void postCommentLimitNotice_throwsNullPointerException_whenExternalIssueIdIsNull() {
    GitHubPostingService service =
        new GitHubPostingService(
            GitHubConfig.of("tok", "owner", "repo"), mockHttpClient, new DefaultIssueFormatter());
    assertThrows(NullPointerException.class, () -> service.postCommentLimitNotice(null, 10));
  }

  @Test
  @SuppressWarnings("unchecked")
  void findDuplicate_returnsNotFound_onInterruptedException() throws Exception {
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new InterruptedException("interrupted"));

    GitHubPostingService service =
        new GitHubPostingService(
            GitHubConfig.of("tok", "owner", "repo"), mockHttpClient, new DefaultIssueFormatter());

    DuplicateSearchResult result = service.findDuplicate(buildEvent("MyException", "oops"));

    assertFalse(result.found());
    assertTrue(Thread.interrupted(), "Service must restore the interrupt flag");
  }

  @Test
  @SuppressWarnings("unchecked")
  void createIssue_returnsFailure_onInterruptedException() throws Exception {
    when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
        .thenThrow(new InterruptedException("interrupted"));

    GitHubPostingService service =
        new GitHubPostingService(
            GitHubConfig.of("tok", "owner", "repo"), mockHttpClient, new DefaultIssueFormatter());

    PostingResult result = service.createIssue(buildEvent("MyException", "oops"));

    assertFalse(result.success());
    assertTrue(result.errorMessage().contains("interrupted"));
    assertTrue(Thread.interrupted(), "Service must restore the interrupt flag");
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
