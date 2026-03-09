package io.hephaistos.observarium.github;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.event.Severity;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * WireMock integration tests for {@link GitHubPostingService}.
 *
 * <p>These tests verify actual HTTP wire format — URLs, headers, JSON payloads, and status code
 * handling — by routing requests through a real {@link java.net.http.HttpClient} to a local
 * WireMock server. They complement the Mockito-based unit tests in {@link
 * GitHubPostingServiceTest}.
 */
@WireMockTest
class GitHubPostingServiceWireMockTest {

  private static final String TOKEN = "test-token-abc";
  private static final String OWNER = "test-owner";
  private static final String REPO = "test-repo";
  private static final String FINGERPRINT = "test-fingerprint-abc123";
  // First 12 chars of FINGERPRINT
  private static final String FINGERPRINT_LABEL = "observarium-test-fingerp";

  private GitHubPostingService buildService(WireMockRuntimeInfo wmInfo) {
    GitHubConfig config =
        new GitHubConfig(TOKEN, OWNER, REPO, "observarium", wmInfo.getHttpBaseUrl());
    return new GitHubPostingService(config);
  }

  // ---------------------------------------------------------------------------
  // findDuplicate tests
  // ---------------------------------------------------------------------------

  @Test
  void findDuplicate_sendsCorrectUrlAndHeaders(WireMockRuntimeInfo wmInfo) {
    stubFor(
        get(urlPathEqualTo("/repos/test-owner/test-repo/issues"))
            .willReturn(aResponse().withStatus(200).withBody("[]")));

    buildService(wmInfo).findDuplicate(buildEvent());

    verify(
        getRequestedFor(urlPathEqualTo("/repos/test-owner/test-repo/issues"))
            .withQueryParam("labels", equalTo(FINGERPRINT_LABEL))
            .withQueryParam("state", equalTo("open"))
            .withHeader("Authorization", equalTo("Bearer " + TOKEN))
            .withHeader("Accept", equalTo("application/vnd.github+json"))
            .withHeader("X-GitHub-Api-Version", equalTo("2022-11-28")));
  }

  @Test
  void findDuplicate_returnsFound_whenIssueExists(WireMockRuntimeInfo wmInfo) {
    stubFor(
        get(urlPathEqualTo("/repos/test-owner/test-repo/issues"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "[{\"number\":42,\"html_url\":\"https://github.com/test-owner/test-repo/issues/42\"}]")));

    DuplicateSearchResult result = buildService(wmInfo).findDuplicate(buildEvent());

    assertTrue(result.found());
    assertEquals("42", result.externalIssueId());
    assertEquals("https://github.com/test-owner/test-repo/issues/42", result.url());
  }

  @Test
  void findDuplicate_returnsNotFound_onEmptyArray(WireMockRuntimeInfo wmInfo) {
    stubFor(
        get(urlPathEqualTo("/repos/test-owner/test-repo/issues"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[]")));

    DuplicateSearchResult result = buildService(wmInfo).findDuplicate(buildEvent());

    assertFalse(result.found());
    assertNull(result.externalIssueId());
  }

  @Test
  void findDuplicate_returnsNotFound_on401(WireMockRuntimeInfo wmInfo) {
    stubFor(
        get(urlPathEqualTo("/repos/test-owner/test-repo/issues"))
            .willReturn(aResponse().withStatus(401).withBody("{\"message\":\"Unauthorized\"}")));

    DuplicateSearchResult result = buildService(wmInfo).findDuplicate(buildEvent());

    assertFalse(result.found());
  }

  @Test
  void findDuplicate_returnsNotFound_on403(WireMockRuntimeInfo wmInfo) {
    stubFor(
        get(urlPathEqualTo("/repos/test-owner/test-repo/issues"))
            .willReturn(aResponse().withStatus(403).withBody("{\"message\":\"Forbidden\"}")));

    DuplicateSearchResult result = buildService(wmInfo).findDuplicate(buildEvent());

    assertFalse(result.found());
  }

  @Test
  void findDuplicate_returnsNotFound_on404(WireMockRuntimeInfo wmInfo) {
    stubFor(
        get(urlPathEqualTo("/repos/test-owner/test-repo/issues"))
            .willReturn(aResponse().withStatus(404).withBody("{\"message\":\"Not Found\"}")));

    DuplicateSearchResult result = buildService(wmInfo).findDuplicate(buildEvent());

    assertFalse(result.found());
  }

  @Test
  void findDuplicate_returnsNotFound_on429(WireMockRuntimeInfo wmInfo) {
    stubFor(
        get(urlPathEqualTo("/repos/test-owner/test-repo/issues"))
            .willReturn(
                aResponse().withStatus(429).withBody("{\"message\":\"API rate limit exceeded\"}")));

    DuplicateSearchResult result = buildService(wmInfo).findDuplicate(buildEvent());

    assertFalse(result.found());
  }

  // ---------------------------------------------------------------------------
  // createIssue tests
  // ---------------------------------------------------------------------------

  @Test
  void createIssue_sendsCorrectUrlHeadersAndBody(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/repos/test-owner/test-repo/issues"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"number\":7,\"html_url\":\"https://github.com/test-owner/test-repo/issues/7\"}")));

    buildService(wmInfo).createIssue(buildEvent());

    verify(
        postRequestedFor(urlPathEqualTo("/repos/test-owner/test-repo/issues"))
            .withHeader("Authorization", equalTo("Bearer " + TOKEN))
            .withHeader("Content-Type", equalTo("application/json"))
            .withHeader("X-GitHub-Api-Version", equalTo("2022-11-28"))
            .withRequestBody(matchingJsonPath("$.title"))
            .withRequestBody(matchingJsonPath("$.body"))
            .withRequestBody(matchingJsonPath("$.labels[?(@ == 'observarium')]"))
            .withRequestBody(matchingJsonPath("$.labels[?(@ == '" + FINGERPRINT_LABEL + "')]")));
  }

  @Test
  void createIssue_returnsSuccess_on201(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/repos/test-owner/test-repo/issues"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"number\":7,\"html_url\":\"https://github.com/test-owner/test-repo/issues/7\"}")));

    PostingResult result = buildService(wmInfo).createIssue(buildEvent());

    assertTrue(result.success());
    assertEquals("7", result.externalIssueId());
    assertEquals("https://github.com/test-owner/test-repo/issues/7", result.url());
    assertNull(result.errorMessage());
  }

  @Test
  void createIssue_returnsFailure_on422(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/repos/test-owner/test-repo/issues"))
            .willReturn(
                aResponse().withStatus(422).withBody("{\"message\":\"Validation Failed\"}")));

    PostingResult result = buildService(wmInfo).createIssue(buildEvent());

    assertFalse(result.success());
    assertNotNull(result.errorMessage());
    assertTrue(result.errorMessage().contains("422"));
  }

  // ---------------------------------------------------------------------------
  // commentOnIssue tests
  // ---------------------------------------------------------------------------

  @Test
  void commentOnIssue_sendsCorrectUrl(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/repos/test-owner/test-repo/issues/42/comments"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"html_url\":\"https://github.com/test-owner/test-repo/issues/42#issuecomment-1\"}")));

    buildService(wmInfo).commentOnIssue("42", buildEvent());

    verify(
        postRequestedFor(urlPathEqualTo("/repos/test-owner/test-repo/issues/42/comments"))
            .withHeader("Authorization", equalTo("Bearer " + TOKEN))
            .withRequestBody(matchingJsonPath("$.body")));
  }

  @Test
  void commentOnIssue_returnsSuccess_on201(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/repos/test-owner/test-repo/issues/42/comments"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"html_url\":\"https://github.com/test-owner/test-repo/issues/42#issuecomment-1\"}")));

    PostingResult result = buildService(wmInfo).commentOnIssue("42", buildEvent());

    assertTrue(result.success());
    assertEquals("42", result.externalIssueId());
    assertEquals("https://github.com/test-owner/test-repo/issues/42#issuecomment-1", result.url());
  }

  @Test
  void commentOnIssue_returnsFailure_on404(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/repos/test-owner/test-repo/issues/42/comments"))
            .willReturn(aResponse().withStatus(404).withBody("{\"message\":\"Not Found\"}")));

    PostingResult result = buildService(wmInfo).commentOnIssue("42", buildEvent());

    assertFalse(result.success());
    assertNotNull(result.errorMessage());
    assertTrue(result.errorMessage().contains("404"));
  }

  // ---------------------------------------------------------------------------
  // Helper
  // ---------------------------------------------------------------------------

  private ExceptionEvent buildEvent() {
    return ExceptionEvent.builder()
        .fingerprint(FINGERPRINT)
        .exceptionClass("java.lang.RuntimeException")
        .message("something went wrong")
        .rawStackTrace("at com.example.Foo.bar(Foo.java:42)")
        .severity(Severity.ERROR)
        .timestamp(Instant.parse("2024-01-15T10:30:00Z"))
        .threadName("main")
        .tags(Map.of("env", "test"))
        .build();
  }
}
