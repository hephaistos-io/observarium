package io.hephaistos.observarium.gitlab;

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
 * WireMock integration tests for {@link GitLabPostingService}.
 *
 * <p>These tests verify actual HTTP wire format — URLs, PRIVATE-TOKEN header, JSON payloads, URL
 * encoding of namespace/project paths, and status code handling — by routing requests through a
 * real {@link java.net.http.HttpClient} to a local WireMock server. They complement the
 * Mockito-based unit tests.
 */
@WireMockTest
class GitLabPostingServiceWireMockTest {

  private static final String PRIVATE_TOKEN = "glpat-test-token";
  private static final String PROJECT_ID = "42";
  private static final String FINGERPRINT = "test-fingerprint-abc123";
  // First 12 chars of FINGERPRINT
  private static final String FINGERPRINT_LABEL = "observarium-test-fingerp";

  private GitLabPostingService buildService(WireMockRuntimeInfo wmInfo) {
    return buildService(wmInfo, PROJECT_ID);
  }

  private GitLabPostingService buildService(WireMockRuntimeInfo wmInfo, String projectId) {
    GitLabConfig config = new GitLabConfig(wmInfo.getHttpBaseUrl(), PRIVATE_TOKEN, projectId);
    return new GitLabPostingService(config);
  }

  // ---------------------------------------------------------------------------
  // findDuplicate tests
  // ---------------------------------------------------------------------------

  @Test
  void findDuplicate_sendsGetRequest_withCorrectUrl(WireMockRuntimeInfo wmInfo) {
    stubFor(
        get(urlPathEqualTo("/api/v4/projects/42/issues"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[]")));

    buildService(wmInfo).findDuplicate(buildEvent());

    verify(
        getRequestedFor(urlPathEqualTo("/api/v4/projects/42/issues"))
            .withQueryParam("labels", equalTo(FINGERPRINT_LABEL))
            .withQueryParam("state", equalTo("opened"))
            .withHeader("PRIVATE-TOKEN", equalTo(PRIVATE_TOKEN)));
  }

  @Test
  void findDuplicate_urlEncodesNamespaceProjectId(WireMockRuntimeInfo wmInfo) {
    // GitLab namespace/project paths must be percent-encoded (/ becomes %2F)
    stubFor(
        get(urlPathEqualTo("/api/v4/projects/mygroup%2Fmyproject/issues"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("[]")));

    buildService(wmInfo, "mygroup/myproject").findDuplicate(buildEvent());

    verify(getRequestedFor(urlPathEqualTo("/api/v4/projects/mygroup%2Fmyproject/issues")));
  }

  @Test
  void findDuplicate_returnsFound_whenIssueExists(WireMockRuntimeInfo wmInfo) {
    stubFor(
        get(urlPathEqualTo("/api/v4/projects/42/issues"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "[{\"iid\":5,\"web_url\":\"https://gitlab.com/test-group/test-repo/-/issues/5\"}]")));

    DuplicateSearchResult result = buildService(wmInfo).findDuplicate(buildEvent());

    assertTrue(result.found());
    assertEquals("5", result.externalIssueId());
    assertEquals("https://gitlab.com/test-group/test-repo/-/issues/5", result.url());
  }

  @Test
  void findDuplicate_returnsNotFound_onEmptyArray(WireMockRuntimeInfo wmInfo) {
    stubFor(
        get(urlPathEqualTo("/api/v4/projects/42/issues"))
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
        get(urlPathEqualTo("/api/v4/projects/42/issues"))
            .willReturn(
                aResponse().withStatus(401).withBody("{\"message\":\"401 Unauthorized\"}")));

    DuplicateSearchResult result = buildService(wmInfo).findDuplicate(buildEvent());

    assertFalse(result.found());
  }

  @Test
  void findDuplicate_returnsNotFound_on404(WireMockRuntimeInfo wmInfo) {
    stubFor(
        get(urlPathEqualTo("/api/v4/projects/42/issues"))
            .willReturn(
                aResponse().withStatus(404).withBody("{\"message\":\"404 Project Not Found\"}")));

    DuplicateSearchResult result = buildService(wmInfo).findDuplicate(buildEvent());

    assertFalse(result.found());
  }

  // ---------------------------------------------------------------------------
  // createIssue tests
  // ---------------------------------------------------------------------------

  @Test
  void createIssue_sendsPostRequest_withCorrectUrlAndBody(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/api/v4/projects/42/issues"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"iid\":5,\"web_url\":\"https://gitlab.com/test-group/test-repo/-/issues/5\"}")));

    buildService(wmInfo).createIssue(buildEvent());

    verify(
        postRequestedFor(urlPathEqualTo("/api/v4/projects/42/issues"))
            .withHeader("PRIVATE-TOKEN", equalTo(PRIVATE_TOKEN))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(matchingJsonPath("$.title"))
            .withRequestBody(matchingJsonPath("$.description"))
            // labels is a comma-separated string: "observarium,observarium-test-fingerp"
            .withRequestBody(matchingJsonPath("$.labels", containing("observarium")))
            .withRequestBody(matchingJsonPath("$.labels", containing(FINGERPRINT_LABEL))));
  }

  @Test
  void createIssue_returnsSuccess_on201(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/api/v4/projects/42/issues"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"iid\":5,\"web_url\":\"https://gitlab.com/test-group/test-repo/-/issues/5\"}")));

    PostingResult result = buildService(wmInfo).createIssue(buildEvent());

    assertTrue(result.success());
    assertEquals("5", result.externalIssueId());
    assertEquals("https://gitlab.com/test-group/test-repo/-/issues/5", result.url());
    assertNull(result.errorMessage());
  }

  @Test
  void createIssue_returnsFailure_on403(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/api/v4/projects/42/issues"))
            .willReturn(aResponse().withStatus(403).withBody("{\"message\":\"403 Forbidden\"}")));

    PostingResult result = buildService(wmInfo).createIssue(buildEvent());

    assertFalse(result.success());
    assertNotNull(result.errorMessage());
    assertTrue(result.errorMessage().contains("403"));
  }

  // ---------------------------------------------------------------------------
  // commentOnIssue tests
  // ---------------------------------------------------------------------------

  @Test
  void commentOnIssue_sendsPostToNotesEndpoint(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/api/v4/projects/42/issues/5/notes"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"id\":101,\"body\":\"test comment\"}")));

    buildService(wmInfo).commentOnIssue("5", buildEvent());

    verify(
        postRequestedFor(urlPathEqualTo("/api/v4/projects/42/issues/5/notes"))
            .withHeader("PRIVATE-TOKEN", equalTo(PRIVATE_TOKEN))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(matchingJsonPath("$.body")));
  }

  @Test
  void commentOnIssue_returnsSuccess_on201(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/api/v4/projects/42/issues/5/notes"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"id\":101,\"body\":\"test comment\"}")));

    PostingResult result = buildService(wmInfo).commentOnIssue("5", buildEvent());

    assertTrue(result.success());
    assertEquals("101", result.externalIssueId());
    assertNull(result.errorMessage());
  }

  @Test
  void commentOnIssue_returnsFailure_on404(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/api/v4/projects/42/issues/5/notes"))
            .willReturn(
                aResponse().withStatus(404).withBody("{\"message\":\"404 Issue Not Found\"}")));

    PostingResult result = buildService(wmInfo).commentOnIssue("5", buildEvent());

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
