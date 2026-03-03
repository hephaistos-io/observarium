# Custom Posting Service

Any backend that is not covered by the built-in modules can be integrated by implementing the `PostingService` interface from `observarium-core`.

---

## The `PostingService` Interface

```java
package io.hephaistos.observarium.posting;

import io.hephaistos.observarium.event.ExceptionEvent;

public interface PostingService {

    /** Human-readable name used in log messages, e.g. "github", "slack-webhook". */
    String name();

    /**
     * Search for an existing issue that matches this event's fingerprint.
     * Return DuplicateSearchResult.notFound() when no duplicate exists.
     * If the backend does not support deduplication, always return notFound().
     */
    DuplicateSearchResult findDuplicate(ExceptionEvent event);

    /**
     * Create a new issue for this event and return the result.
     * Return PostingResult.failure(...) on error instead of throwing.
     */
    PostingResult createIssue(ExceptionEvent event);

    /**
     * Append a recurrence note to an existing issue.
     * externalIssueId is the value from the DuplicateSearchResult returned by findDuplicate().
     */
    PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event);
}
```

### Key types

**`ExceptionEvent`** — the scrubbed, fingerprinted event. Relevant fields:

| Field | Type | Description |
|---|---|---|
| `fingerprint()` | `String` | SHA-256 hex string identifying this exception structure. Use as the dedup key. |
| `exceptionClass()` | `String` | Fully-qualified exception class name. |
| `message()` | `String` | Scrubbed exception message, may be `null`. |
| `rawStackTrace()` | `String` | Full scrubbed stack trace as a single string. |
| `severity()` | `Severity` | `INFO`, `WARNING`, `ERROR`, or `FATAL`. |
| `timestamp()` | `Instant` | When the exception was captured. |
| `threadName()` | `String` | Name of the capturing thread. |
| `traceId()` | `String` | OpenTelemetry trace ID from MDC, may be `null`. |
| `spanId()` | `String` | OpenTelemetry span ID from MDC, may be `null`. |
| `tags()` | `Map<String, String>` | Caller-supplied key-value tags. |
| `stackTrace()` | `List<String>` | Structured list of individual stack frame strings. |
| `extra()` | `Map<String, String>` | System-collected metadata added automatically by the pipeline. |

**`PostingResult`** — return value for `createIssue` and `commentOnIssue`:

```java
// Success: supply your backend's issue identifier and a human-readable URL
PostingResult.success("ISSUE-42", "https://example.com/issues/42");

// Failure: supply an error description; do not throw from PostingService methods
PostingResult.failure("HTTP 429: rate limit exceeded");
```

**`DuplicateSearchResult`** — return value for `findDuplicate`:

```java
DuplicateSearchResult.notFound();                                         // no duplicate
DuplicateSearchResult.found("ISSUE-42", "https://example.com/issues/42"); // duplicate exists
```

### Deduplication strategy recommendations

- **Embed the fingerprint as metadata** — write `event.fingerprint()` into a field, label, tag, or HTML comment that you can query later. The default `IssueFormatter` implementation produces `<!-- observarium:fingerprint:<sha256> -->` via `fingerprintMarker(fingerprint)` for Markdown bodies. You can implement the `IssueFormatter` interface with your own marker format.
- **Search only open/active issues** — if a duplicate issue was closed or resolved, creating a fresh issue is usually the right behaviour.
- **Fail safe to `notFound()`** — if the search API returns an error, return `DuplicateSearchResult.notFound()` so a new issue is created rather than swallowing the event.
- **No dedup? Return `notFound()` always** — backends like email cannot search previous messages; returning `notFound()` unconditionally is correct for them.

---

## Full Example: Slack Webhook Posting Service

This implementation sends a formatted Slack message for every new exception. Because Slack message history is not searchable via incoming webhooks, deduplication is not implemented.

```java
import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class SlackWebhookPostingService implements PostingService {

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private final String webhookUrl;

    public SlackWebhookPostingService(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    @Override
    public String name() {
        return "slack-webhook";
    }

    @Override
    public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
        // Incoming webhooks cannot search Slack history; always create a new message.
        return DuplicateSearchResult.notFound();
    }

    @Override
    public PostingResult createIssue(ExceptionEvent event) {
        String text = buildSlackMessage(event);
        String json = "{\"text\":" + jsonString(text) + "}";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response =
                HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                // Slack webhooks do not return an issue ID or URL
                return PostingResult.success("slack-message", webhookUrl);
            }
            return PostingResult.failure(
                "Slack returned HTTP " + response.statusCode() + ": " + response.body());

        } catch (Exception e) {
            return PostingResult.failure("Slack webhook failed: " + e.getMessage());
        }
    }

    @Override
    public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
        // No dedup, so this method is never called in practice.
        return createIssue(event);
    }

    private String buildSlackMessage(ExceptionEvent event) {
        String exClass = event.exceptionClass();
        int dot = exClass.lastIndexOf('.');
        String shortClass = dot >= 0 ? exClass.substring(dot + 1) : exClass;

        var builder = new StringBuilder();
        builder.append(":rotating_light: *").append(event.severity()).append("* — `")
               .append(shortClass).append("`");
        if (event.message() != null) {
            builder.append(": ").append(event.message());
        }
        builder.append("\n*Thread:* ").append(event.threadName());
        builder.append("  *At:* ").append(event.timestamp());
        if (event.traceId() != null) {
            builder.append("\n*Trace:* `").append(event.traceId()).append("`");
        }
        if (!event.tags().isEmpty()) {
            builder.append("\n*Tags:* ").append(event.tags());
        }
        builder.append("\n*Fingerprint:* `").append(event.fingerprint()).append("`");
        return builder.toString();
    }

    private static String jsonString(String value) {
        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            + "\"";
    }
}
```

---

## Registering a Custom Posting Service

### Plain Java (builder)

```java
Observarium obs = Observarium.builder()
    .addPostingService(new SlackWebhookPostingService(
        System.getenv("SLACK_WEBHOOK_URL")))
    .build();
```

Multiple services can be registered and all receive every event:

```java
Observarium obs = Observarium.builder()
    .addPostingService(new GitHubPostingService(GitHubConfig.of(token, "owner", "repo")))
    .addPostingService(new SlackWebhookPostingService(webhookUrl))
    .build();
```

### Spring Boot (`@Bean`)

Declare a `@Bean` of type `PostingService` (or `List<PostingService>`). The auto-configuration collects all `PostingService` beans from the application context and passes them to the builder.

```java
import io.hephaistos.observarium.posting.PostingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservariumConfig {

    @Bean
    public PostingService slackPostingService() {
        return new SlackWebhookPostingService(System.getenv("SLACK_WEBHOOK_URL"));
    }
}
```

### Quarkus (`@Produces`)

Declare a CDI producer. The Quarkus extension collects all beans assignable to `PostingService`.

```java
import io.hephaistos.observarium.posting.PostingService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class ObservariumProducers {

    @Produces
    @ApplicationScoped
    public PostingService slackPostingService() {
        return new SlackWebhookPostingService(System.getenv("SLACK_WEBHOOK_URL"));
    }
}
```
