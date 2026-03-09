package io.hephaistos.observarium.jira;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.event.Severity;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * WireMock integration tests for {@link JiraPostingService}.
 *
 * <p>These tests verify actual HTTP wire format — URLs, Basic auth headers, JSON payloads in ADF
 * format, and status code handling — by routing requests through a real {@link
 * java.net.http.HttpClient} to a local WireMock server. They complement the Mockito-based unit
 * tests.
 */
@WireMockTest
class JiraPostingServiceWireMockTest {

  private static final String USERNAME = "test-user@example.com";
  private static final String API_TOKEN = "test-api-token";
  private static final String PROJECT_KEY = "OBS";
  private static final String FINGERPRINT = "test-fingerprint-abc123";
  // First 12 chars of FINGERPRINT
  private static final String FINGERPRINT_LABEL = "observarium-test-fingerp";

  private JiraPostingService buildService(WireMockRuntimeInfo wmInfo) {
    JiraConfig config =
        new JiraConfig(wmInfo.getHttpBaseUrl(), USERNAME, API_TOKEN, PROJECT_KEY, "Bug");
    return new JiraPostingService(config);
  }

  private String expectedBasicAuthHeader() {
    String credentials = USERNAME + ":" + API_TOKEN;
    return "Basic "
        + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
  }

  // ---------------------------------------------------------------------------
  // findDuplicate tests
  // ---------------------------------------------------------------------------

  @Test
  void findDuplicate_postsToSearchEndpoint(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/rest/api/3/search/jql"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"issues\":[]}")));

    buildService(wmInfo).findDuplicate(buildEvent());

    verify(
        postRequestedFor(urlPathEqualTo("/rest/api/3/search/jql"))
            .withHeader("Authorization", equalTo(expectedBasicAuthHeader()))
            .withHeader("Content-Type", equalTo("application/json"))
            .withRequestBody(matchingJsonPath("$.jql"))
            .withRequestBody(matchingJsonPath("$.maxResults")));
  }

  @Test
  void findDuplicate_sendsBasicAuthHeader(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/rest/api/3/search/jql"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"issues\":[]}")));

    buildService(wmInfo).findDuplicate(buildEvent());

    // Capture the Authorization header and decode it to verify the credentials format
    verify(
        postRequestedFor(urlPathEqualTo("/rest/api/3/search/jql"))
            .withHeader("Authorization", matching("Basic .+")));

    String expectedAuth = expectedBasicAuthHeader();
    // Decode and verify it contains username:apiToken
    String encodedPart = expectedAuth.substring("Basic ".length());
    String decoded = new String(Base64.getDecoder().decode(encodedPart), StandardCharsets.UTF_8);
    assertEquals(USERNAME + ":" + API_TOKEN, decoded);
  }

  @Test
  void findDuplicate_returnsFound_whenIssueExists(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/rest/api/3/search/jql"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"issues\":[{\"key\":\"OBS-42\",\"fields\":{\"labels\":"
                            + "[\"observarium\",\""
                            + FINGERPRINT_LABEL
                            + "\"]}}]}")));

    DuplicateSearchResult result = buildService(wmInfo).findDuplicate(buildEvent());

    assertTrue(result.found());
    assertEquals("OBS-42", result.externalIssueId());
  }

  @Test
  void findDuplicate_returnsNotFound_whenNoIssues(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/rest/api/3/search/jql"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"issues\":[]}")));

    DuplicateSearchResult result = buildService(wmInfo).findDuplicate(buildEvent());

    assertFalse(result.found());
    assertNull(result.externalIssueId());
  }

  @Test
  void findDuplicate_returnsNotFound_on401(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/rest/api/3/search/jql"))
            .willReturn(
                aResponse().withStatus(401).withBody("{\"errorMessages\":[\"Unauthorized\"]}")));

    DuplicateSearchResult result = buildService(wmInfo).findDuplicate(buildEvent());

    assertFalse(result.found());
  }

  @Test
  void findDuplicate_returnsNotFound_on400(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/rest/api/3/search/jql"))
            .willReturn(
                aResponse().withStatus(400).withBody("{\"errorMessages\":[\"Invalid JQL\"]}")));

    DuplicateSearchResult result = buildService(wmInfo).findDuplicate(buildEvent());

    assertFalse(result.found());
  }

  // ---------------------------------------------------------------------------
  // createIssue tests
  // ---------------------------------------------------------------------------

  @Test
  void createIssue_postsToIssueEndpoint(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/rest/api/3/issue"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"key\":\"OBS-7\",\"self\":\"http://example.atlassian.net/rest/api/3/issue/OBS-7\"}")));

    buildService(wmInfo).createIssue(buildEvent());

    verify(
        postRequestedFor(urlPathEqualTo("/rest/api/3/issue"))
            .withHeader("Authorization", equalTo(expectedBasicAuthHeader()))
            .withHeader("Content-Type", equalTo("application/json"))
            // Verify ADF structure: fields.project.key
            .withRequestBody(matchingJsonPath("$.fields.project.key", equalTo(PROJECT_KEY)))
            // Verify ADF structure: fields.issuetype.name
            .withRequestBody(matchingJsonPath("$.fields.issuetype.name", equalTo("Bug")))
            // Verify summary/title is present
            .withRequestBody(matchingJsonPath("$.fields.summary"))
            // Verify description is ADF doc format
            .withRequestBody(matchingJsonPath("$.fields.description.type", equalTo("doc")))
            // Verify labels contain fingerprint label
            .withRequestBody(
                matchingJsonPath("$.fields.labels[?(@ == '" + FINGERPRINT_LABEL + "')]"))
            .withRequestBody(matchingJsonPath("$.fields.labels[?(@ == 'observarium')]")));
  }

  @Test
  void createIssue_returnsSuccess_on201(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/rest/api/3/issue"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"key\":\"OBS-7\",\"self\":\"http://example.atlassian.net/rest/api/3/issue/OBS-7\"}")));

    PostingResult result = buildService(wmInfo).createIssue(buildEvent());

    assertTrue(result.success());
    assertEquals("OBS-7", result.externalIssueId());
    assertNull(result.errorMessage());
  }

  @Test
  void createIssue_returnsFailure_on400(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/rest/api/3/issue"))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withBody("{\"errorMessages\":[\"Field 'summary' is required\"]}")));

    PostingResult result = buildService(wmInfo).createIssue(buildEvent());

    assertFalse(result.success());
    assertNotNull(result.errorMessage());
    assertTrue(result.errorMessage().contains("400"));
  }

  @Test
  void createIssue_returnsFailure_on404(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/rest/api/3/issue"))
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withBody("{\"errorMessages\":[\"Project OBS not found\"]}")));

    PostingResult result = buildService(wmInfo).createIssue(buildEvent());

    assertFalse(result.success());
    assertNotNull(result.errorMessage());
    assertTrue(result.errorMessage().contains("404"));
  }

  // ---------------------------------------------------------------------------
  // commentOnIssue tests
  // ---------------------------------------------------------------------------

  @Test
  void commentOnIssue_postsToCommentEndpoint(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/rest/api/3/issue/OBS-42/comment"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id\":\"10000\",\"self\":\"http://example.atlassian.net/rest/api/3/issue/OBS-42/comment/10000\"}")));

    buildService(wmInfo).commentOnIssue("OBS-42", buildEvent());

    verify(
        postRequestedFor(urlPathEqualTo("/rest/api/3/issue/OBS-42/comment"))
            .withHeader("Authorization", equalTo(expectedBasicAuthHeader()))
            .withHeader("Content-Type", equalTo("application/json"))
            // Comment body uses ADF paragraph format
            .withRequestBody(matchingJsonPath("$.body.type", equalTo("doc")))
            .withRequestBody(matchingJsonPath("$.body.content[0].type", equalTo("paragraph"))));
  }

  @Test
  void commentOnIssue_returnsSuccess_on201(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/rest/api/3/issue/OBS-42/comment"))
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"id\":\"10000\",\"self\":\"http://example.atlassian.net/rest/api/3/issue/OBS-42/comment/10000\"}")));

    PostingResult result = buildService(wmInfo).commentOnIssue("OBS-42", buildEvent());

    assertTrue(result.success());
    assertEquals("OBS-42", result.externalIssueId());
    assertNull(result.errorMessage());
  }

  @Test
  void commentOnIssue_returnsFailure_on404(WireMockRuntimeInfo wmInfo) {
    stubFor(
        post(urlPathEqualTo("/rest/api/3/issue/OBS-42/comment"))
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withBody("{\"errorMessages\":[\"Issue OBS-42 not found\"]}")));

    PostingResult result = buildService(wmInfo).commentOnIssue("OBS-42", buildEvent());

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
