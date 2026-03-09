package io.hephaistos.observarium.gitlab;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.posting.DefaultIssueFormatter;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.IssueFormatter;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PostingService implementation that creates and comments on GitLab issues.
 *
 * <p>Each distinct exception fingerprint is represented by a label of the form {@code
 * observarium-{first12charsOfFingerprint}}. Duplicate detection searches for open issues carrying
 * that label. Issue creation attaches both the {@code observarium} umbrella label and the
 * fingerprint-specific label so that the project's issue list stays filterable at two
 * granularities.
 */
public class GitLabPostingService implements PostingService {

  private static final Logger log = LoggerFactory.getLogger(GitLabPostingService.class);

  private static final String SERVICE_NAME = "gitlab";
  private static final String PRIVATE_TOKEN_HEADER = "PRIVATE-TOKEN";
  private static final String CONTENT_TYPE_HEADER = "Content-Type";
  private static final String APPLICATION_JSON = "application/json";
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

  private final GitLabConfig config;
  private final HttpClient httpClient;
  private final Gson gson;
  private final IssueFormatter formatter;

  public GitLabPostingService(GitLabConfig config) {
    this(
        config,
        HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build(),
        new Gson(),
        new DefaultIssueFormatter());
  }

  public GitLabPostingService(GitLabConfig config, IssueFormatter formatter) {
    this(
        config,
        HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build(),
        new Gson(),
        formatter);
  }

  /** Package-private constructor for testing with a custom HttpClient. */
  GitLabPostingService(
      GitLabConfig config, HttpClient httpClient, Gson gson, IssueFormatter formatter) {
    this.config = java.util.Objects.requireNonNull(config, "config must not be null");
    this.httpClient = java.util.Objects.requireNonNull(httpClient, "httpClient must not be null");
    this.gson = java.util.Objects.requireNonNull(gson, "gson must not be null");
    this.formatter = java.util.Objects.requireNonNull(formatter, "formatter must not be null");
  }

  @Override
  public String name() {
    return SERVICE_NAME;
  }

  /**
   * Searches for an open GitLab issue that carries the fingerprint label derived from this event.
   * Returns the first match, or {@link DuplicateSearchResult#notFound()} if none exists or if the
   * request fails.
   */
  @Override
  public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
    String label = fingerprintLabel(event.fingerprint());
    String url =
        config.baseUrl()
            + "/api/v4/projects/"
            + encodedProjectId()
            + "/issues?labels="
            + label
            + "&state=opened";

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(REQUEST_TIMEOUT)
            .header(PRIVATE_TOKEN_HEADER, config.privateToken())
            .GET()
            .build();

    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (!isSuccess(response.statusCode())) {
        log.warn(
            "GitLab duplicate search returned HTTP {}: {}", response.statusCode(), response.body());
        return DuplicateSearchResult.notFound();
      }

      JsonArray issues = gson.fromJson(response.body(), JsonArray.class);
      if (issues == null || issues.isEmpty()) {
        return DuplicateSearchResult.notFound();
      }

      JsonObject first = issues.get(0).getAsJsonObject();
      String iid = String.valueOf(first.get("iid").getAsLong());
      String webUrl = first.get("web_url").getAsString();
      return DuplicateSearchResult.found(iid, webUrl);

    } catch (Exception e) {
      log.error("Failed to search for duplicate GitLab issue", e);
      return DuplicateSearchResult.notFound();
    }
  }

  /**
   * Creates a new GitLab issue for the given event, labelled with both the {@code observarium}
   * umbrella label and the fingerprint-specific label.
   */
  @Override
  public PostingResult createIssue(ExceptionEvent event) {
    String url = config.baseUrl() + "/api/v4/projects/" + encodedProjectId() + "/issues";

    String label = fingerprintLabel(event.fingerprint());
    JsonObject body = new JsonObject();
    body.addProperty("title", formatter.title(event));
    body.addProperty("description", formatter.markdownBody(event));
    body.addProperty("labels", "observarium," + label);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(REQUEST_TIMEOUT)
            .header(PRIVATE_TOKEN_HEADER, config.privateToken())
            .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
            .build();

    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (!isSuccess(response.statusCode())) {
        String msg =
            "GitLab issue creation returned HTTP " + response.statusCode() + ": " + response.body();
        log.error(msg);
        return PostingResult.failure(msg);
      }

      JsonObject created = gson.fromJson(response.body(), JsonObject.class);
      String iid = String.valueOf(created.get("iid").getAsLong());
      String webUrl = created.get("web_url").getAsString();
      return PostingResult.success(iid, webUrl);

    } catch (Exception e) {
      log.error("Failed to create GitLab issue", e);
      return PostingResult.failure("Failed to create GitLab issue: " + e.getMessage());
    }
  }

  /**
   * Posts a comment on an existing GitLab issue identified by its internal issue ID ({@code iid}).
   */
  @Override
  public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
    String url =
        config.baseUrl()
            + "/api/v4/projects/"
            + encodedProjectId()
            + "/issues/"
            + externalIssueId
            + "/notes";

    JsonObject body = new JsonObject();
    body.addProperty("body", formatter.markdownComment(event));

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(REQUEST_TIMEOUT)
            .header(PRIVATE_TOKEN_HEADER, config.privateToken())
            .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
            .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
            .build();

    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (!isSuccess(response.statusCode())) {
        String msg =
            "GitLab comment creation returned HTTP "
                + response.statusCode()
                + ": "
                + response.body();
        log.error(msg);
        return PostingResult.failure(msg);
      }

      JsonObject note = gson.fromJson(response.body(), JsonObject.class);
      String noteId = String.valueOf(note.get("id").getAsLong());
      return PostingResult.success(noteId, null);

    } catch (Exception e) {
      log.error("Failed to comment on GitLab issue {}", externalIssueId, e);
      return PostingResult.failure("Failed to comment on GitLab issue: " + e.getMessage());
    }
  }

  // --- helpers ---

  private String encodedProjectId() {
    return URLEncoder.encode(config.projectId(), StandardCharsets.UTF_8);
  }

  private boolean isSuccess(int statusCode) {
    return statusCode >= 200 && statusCode < 300;
  }
}
