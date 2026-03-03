package io.hephaistos.observarium.jira;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.IssueFormatter;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * {@link PostingService} implementation that creates and manages Jira issues.
 *
 * <p>Issues are identified across runs by a label derived from the first 12 characters of the
 * event fingerprint: {@code observarium-<hash12>}. The service queries for open issues carrying
 * that label before creating a new one, and adds a comment when a duplicate is found.
 *
 * <p>All communication with Jira uses the v3 REST API and Basic authentication
 * ({@code username:apiToken} encoded as Base64).
 */
public class JiraPostingService implements PostingService {

    private static final Logger log = LoggerFactory.getLogger(JiraPostingService.class);

    /** Number of fingerprint characters used in the Jira label. */
    private static final int FINGERPRINT_PREFIX_LENGTH = 12;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final JiraConfig config;
    private final HttpClient httpClient;
    private final Gson gson;

    public JiraPostingService(JiraConfig config) {
        this(config, HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build(), new Gson());
    }

    /**
     * Package-private constructor used in tests to inject a custom {@link HttpClient}.
     */
    JiraPostingService(JiraConfig config, HttpClient httpClient, Gson gson) {
        this.config = config;
        this.httpClient = httpClient;
        this.gson = gson;
    }

    @Override
    public String name() {
        return "jira";
    }

    // -------------------------------------------------------------------------
    // PostingService implementation
    // -------------------------------------------------------------------------

    /**
     * Searches for an open Jira issue whose labels include the fingerprint-derived marker.
     *
     * <p>Uses the JQL:
     * {@code project = {projectKey} AND labels = "observarium-{hash12}" AND statusCategory != Done}
     */
    @Override
    public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
        String label = fingerprintLabel(event.fingerprint());
        String jql = String.format(
            "project = \"%s\" AND labels = \"%s\" AND statusCategory != Done",
            config.projectKey(), label
        );

        JsonObject body = new JsonObject();
        body.addProperty("jql", jql);
        body.addProperty("maxResults", 1);

        JsonArray fields = new JsonArray();
        fields.add("summary");
        fields.add("status");
        body.add("fields", fields);

        String url = config.baseUrl() + "/rest/api/3/search/jql";

        try {
            HttpResponse<String> response = post(url, gson.toJson(body));

            if (!isSuccess(response.statusCode())) {
                log.warn("Jira duplicate search returned HTTP {}: {}", response.statusCode(), response.body());
                return DuplicateSearchResult.notFound();
            }

            JsonObject parsed = gson.fromJson(response.body(), JsonObject.class);
            JsonArray issues = parsed.getAsJsonArray("issues");

            if (issues == null || issues.isEmpty()) {
                return DuplicateSearchResult.notFound();
            }

            JsonObject issue = issues.get(0).getAsJsonObject();
            String key = issue.get("key").getAsString();
            String self = issue.get("self").getAsString();
            String issueUrl = browseUrl(key);

            log.debug("Jira duplicate found: {} ({})", key, issueUrl);
            return DuplicateSearchResult.found(key, issueUrl);

        } catch (Exception e) {
            log.error("Error searching for Jira duplicate for fingerprint {}", event.fingerprint(), e);
            return DuplicateSearchResult.notFound();
        }
    }

    /**
     * Creates a new Jira issue for the given event using ADF (Atlassian Document Format) for
     * the description body. The issue is tagged with both {@code observarium} and the
     * fingerprint-derived label so duplicates can be found in subsequent runs.
     */
    @Override
    public PostingResult createIssue(ExceptionEvent event) {
        String title = IssueFormatter.title(event);
        String markdownBody = IssueFormatter.markdownBody(event);
        String label = fingerprintLabel(event.fingerprint());

        JsonObject body = buildCreateIssueBody(title, markdownBody, label);
        String url = config.baseUrl() + "/rest/api/3/issue";

        try {
            HttpResponse<String> response = post(url, gson.toJson(body));

            if (!isSuccess(response.statusCode())) {
                String msg = String.format("Jira createIssue failed with HTTP %d: %s",
                    response.statusCode(), response.body());
                log.error(msg);
                return PostingResult.failure(msg);
            }

            JsonObject parsed = gson.fromJson(response.body(), JsonObject.class);
            String key = parsed.get("key").getAsString();
            String issueUrl = browseUrl(key);

            log.info("Jira issue created: {} ({})", key, issueUrl);
            return PostingResult.success(key, issueUrl);

        } catch (Exception e) {
            log.error("Error creating Jira issue for fingerprint {}", event.fingerprint(), e);
            return PostingResult.failure("Exception creating Jira issue: " + e.getMessage());
        }
    }

    /**
     * Adds a comment to an existing Jira issue when the same exception recurs.
     *
     * @param externalIssueId the Jira issue key, e.g. {@code OBS-42}
     */
    @Override
    public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
        String commentText = IssueFormatter.markdownComment(event);
        JsonObject body = buildCommentBody(commentText);
        String url = config.baseUrl() + "/rest/api/3/issue/" + externalIssueId + "/comment";

        try {
            HttpResponse<String> response = post(url, gson.toJson(body));

            if (!isSuccess(response.statusCode())) {
                String msg = String.format("Jira commentOnIssue failed with HTTP %d: %s",
                    response.statusCode(), response.body());
                log.error(msg);
                return PostingResult.failure(msg);
            }

            String issueUrl = browseUrl(externalIssueId);
            log.info("Jira comment added to issue {}", externalIssueId);
            return PostingResult.success(externalIssueId, issueUrl);

        } catch (Exception e) {
            log.error("Error adding comment to Jira issue {}", externalIssueId, e);
            return PostingResult.failure("Exception commenting on Jira issue: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Request body builders
    // -------------------------------------------------------------------------

    private JsonObject buildCreateIssueBody(String title, String markdownBody, String fingerprintLabel) {
        JsonObject root = new JsonObject();
        JsonObject fields = new JsonObject();

        // Project
        JsonObject project = new JsonObject();
        project.addProperty("key", config.projectKey());
        fields.add("project", project);

        // Issue type
        JsonObject issueType = new JsonObject();
        issueType.addProperty("name", config.issueType());
        fields.add("issuetype", issueType);

        // Summary
        fields.addProperty("summary", title);

        // Description — ADF codeBlock wrapping the markdown content
        fields.add("description", adfDocument(adfCodeBlock(markdownBody)));

        // Labels
        JsonArray labels = new JsonArray();
        labels.add("observarium");
        labels.add(fingerprintLabel);
        fields.add("labels", labels);

        root.add("fields", fields);
        return root;
    }

    private JsonObject buildCommentBody(String text) {
        JsonObject root = new JsonObject();
        root.add("body", adfDocument(adfParagraph(text)));
        return root;
    }

    // -------------------------------------------------------------------------
    // ADF helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a minimal ADF document node wrapping the supplied content node.
     *
     * <pre>{@code
     * { "version": 1, "type": "doc", "content": [ <contentNode> ] }
     * }</pre>
     */
    private JsonObject adfDocument(JsonObject contentNode) {
        JsonObject doc = new JsonObject();
        doc.addProperty("version", 1);
        doc.addProperty("type", "doc");

        JsonArray content = new JsonArray();
        content.add(contentNode);
        doc.add("content", content);

        return doc;
    }

    /**
     * Builds an ADF {@code codeBlock} node containing a single text leaf.
     *
     * <pre>{@code
     * { "type": "codeBlock", "content": [ { "type": "text", "text": "<text>" } ] }
     * }</pre>
     */
    private JsonObject adfCodeBlock(String text) {
        JsonObject textNode = new JsonObject();
        textNode.addProperty("type", "text");
        textNode.addProperty("text", text);

        JsonArray content = new JsonArray();
        content.add(textNode);

        JsonObject codeBlock = new JsonObject();
        codeBlock.addProperty("type", "codeBlock");
        codeBlock.add("content", content);

        return codeBlock;
    }

    /**
     * Builds an ADF {@code paragraph} node containing a single text leaf.
     *
     * <pre>{@code
     * { "type": "paragraph", "content": [ { "type": "text", "text": "<text>" } ] }
     * }</pre>
     */
    private JsonObject adfParagraph(String text) {
        JsonObject textNode = new JsonObject();
        textNode.addProperty("type", "text");
        textNode.addProperty("text", text);

        JsonArray content = new JsonArray();
        content.add(textNode);

        JsonObject paragraph = new JsonObject();
        paragraph.addProperty("type", "paragraph");
        paragraph.add("content", content);

        return paragraph;
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    private HttpResponse<String> post(String url, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", basicAuthHeader())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
            .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * Returns {@code true} for any 2xx HTTP status code.
     */
    private static boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    // -------------------------------------------------------------------------
    // Auth / URL helpers
    // -------------------------------------------------------------------------

    /**
     * Constructs the {@code Authorization: Basic ...} header value by Base64-encoding
     * {@code username:apiToken}.
     */
    String basicAuthHeader() {
        String credentials = config.username() + ":" + config.apiToken();
        String encoded = Base64.getEncoder()
            .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    /**
     * Returns the human-readable browse URL for a Jira issue key, e.g.
     * {@code https://mycompany.atlassian.net/browse/OBS-42}.
     */
    private String browseUrl(String issueKey) {
        return config.baseUrl() + "/browse/" + issueKey;
    }

    /**
     * Derives the fingerprint-based Jira label from the first
     * {@value #FINGERPRINT_PREFIX_LENGTH} characters of the fingerprint hash.
     *
     * <p>Example: fingerprint {@code "abc123def456xyz"} → label {@code "observarium-abc123def456"}.
     */
    static String fingerprintLabel(String fingerprint) {
        String prefix = fingerprint.length() > FINGERPRINT_PREFIX_LENGTH
            ? fingerprint.substring(0, FINGERPRINT_PREFIX_LENGTH)
            : fingerprint;
        return "observarium-" + prefix;
    }
}
