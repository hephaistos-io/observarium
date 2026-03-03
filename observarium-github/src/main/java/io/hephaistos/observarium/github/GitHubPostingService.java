package io.hephaistos.observarium.github;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.IssueFormatter;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * {@link PostingService} implementation that creates and manages GitHub issues.
 *
 * <p>Uses the GitHub REST API v3 ({@code https://api.github.com}) via the JDK built-in
 * {@link HttpClient}. JSON serialisation is handled by Gson.
 *
 * <p>All HTTP errors (non-2xx responses) are surfaced as {@link PostingResult#failure} values
 * rather than exceptions so that calling code can decide how to handle them.
 */
public class GitHubPostingService implements PostingService {

    private static final Logger log = LoggerFactory.getLogger(GitHubPostingService.class);

    private static final String API_BASE = "https://api.github.com";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final GitHubConfig config;
    private final HttpClient httpClient;
    private final Gson gson;

    public GitHubPostingService(GitHubConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        this.gson = new Gson();
    }

    /** Package-private constructor for tests — allows injecting a mock/stub {@link HttpClient}. */
    GitHubPostingService(GitHubConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
        this.gson = new Gson();
    }

    @Override
    public String name() {
        return "github";
    }

    /**
     * Searches for an open GitHub issue carrying the fingerprint label.
     *
     * <p>Uses a label of the form {@code observarium-{first12CharsOfFingerprint}} so that
     * deduplication relies on the GitHub Issues API (label filtering) rather than the
     * Search API (which does not index HTML comments).
     */
    @Override
    public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
        String label = fingerprintLabel(event.fingerprint());
        String url = API_BASE + "/repos/" + config.owner() + "/" + config.repo()
                + "/issues?labels=" + URLEncoder.encode(label, StandardCharsets.UTF_8)
                + "&state=open";

        log.debug("Searching for duplicate issue. fingerprint={} label={}", event.fingerprint(), label);

        try {
            HttpRequest request = buildGetRequest(url);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (!isSuccess(response.statusCode())) {
                log.warn("GitHub duplicate search returned HTTP {}. body={}", response.statusCode(), response.body());
                return DuplicateSearchResult.notFound();
            }

            JsonArray issues = gson.fromJson(response.body(), JsonArray.class);
            if (issues == null || issues.isEmpty()) {
                return DuplicateSearchResult.notFound();
            }

            JsonObject issue = issues.get(0).getAsJsonObject();
            String issueNumber = String.valueOf(issue.get("number").getAsInt());
            String htmlUrl = issue.get("html_url").getAsString();

            log.debug("Found duplicate issue. number={} url={}", issueNumber, htmlUrl);
            return DuplicateSearchResult.found(issueNumber, htmlUrl);

        } catch (IOException | InterruptedException e) {
            log.error("Failed to search for duplicate GitHub issue. fingerprint={}", event.fingerprint(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return DuplicateSearchResult.notFound();
        }
    }

    /**
     * Creates a new GitHub issue for the given event.
     *
     * <p>The issue title and body are produced by {@link IssueFormatter}. The configured
     * {@link GitHubConfig#labelPrefix()} label is applied so that issues can be filtered in
     * the GitHub UI.
     */
    @Override
    public PostingResult createIssue(ExceptionEvent event) {
        String url = API_BASE + "/repos/" + config.owner() + "/" + config.repo() + "/issues";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("title", IssueFormatter.title(event));
        requestBody.addProperty("body", IssueFormatter.markdownBody(event));

        JsonArray labels = new JsonArray();
        labels.add(config.labelPrefix());
        labels.add(fingerprintLabel(event.fingerprint()));
        requestBody.add("labels", labels);

        log.debug("Creating GitHub issue. fingerprint={}", event.fingerprint());

        try {
            HttpRequest request = buildPostRequest(url, gson.toJson(requestBody));
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (!isSuccess(response.statusCode())) {
                String message = "GitHub API returned HTTP " + response.statusCode()
                        + " when creating issue: " + response.body();
                log.warn(message);
                return PostingResult.failure(message);
            }

            JsonObject responseBody = gson.fromJson(response.body(), JsonObject.class);
            String issueNumber = String.valueOf(responseBody.get("number").getAsInt());
            String htmlUrl = responseBody.get("html_url").getAsString();

            log.info("Created GitHub issue. number={} url={}", issueNumber, htmlUrl);
            return PostingResult.success(issueNumber, htmlUrl);

        } catch (IOException | InterruptedException e) {
            log.error("Failed to create GitHub issue. fingerprint={}", event.fingerprint(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return PostingResult.failure("Failed to create GitHub issue: " + e.getMessage());
        }
    }

    /**
     * Posts a comment on an existing GitHub issue to record a recurrence of the exception.
     *
     * @param externalIssueId the string issue number returned by a previous call to
     *                        {@link #createIssue} or {@link #findDuplicate}
     */
    @Override
    public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
        String url = API_BASE + "/repos/" + config.owner() + "/" + config.repo()
                + "/issues/" + externalIssueId + "/comments";

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("body", IssueFormatter.markdownComment(event));

        log.debug("Adding comment to GitHub issue. number={} fingerprint={}", externalIssueId, event.fingerprint());

        try {
            HttpRequest request = buildPostRequest(url, gson.toJson(requestBody));
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (!isSuccess(response.statusCode())) {
                String message = "GitHub API returned HTTP " + response.statusCode()
                        + " when commenting on issue " + externalIssueId + ": " + response.body();
                log.warn(message);
                return PostingResult.failure(message);
            }

            JsonObject responseBody = gson.fromJson(response.body(), JsonObject.class);
            String commentUrl = responseBody.get("html_url").getAsString();

            log.debug("Added comment to GitHub issue. number={} commentUrl={}", externalIssueId, commentUrl);
            return PostingResult.success(externalIssueId, commentUrl);

        } catch (IOException | InterruptedException e) {
            log.error("Failed to comment on GitHub issue. number={} fingerprint={}", externalIssueId, event.fingerprint(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return PostingResult.failure("Failed to comment on GitHub issue " + externalIssueId + ": " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    static String fingerprintLabel(String fingerprint) {
        String hash12 = fingerprint.length() > 12 ? fingerprint.substring(0, 12) : fingerprint;
        return "observarium-" + hash12;
    }

    private HttpRequest buildGetRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + config.token())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();
    }

    private HttpRequest buildPostRequest(String url, String jsonBody) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + config.token())
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
    }

    private static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }
}
