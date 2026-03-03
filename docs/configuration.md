# Configuration Reference

---

## Builder API (`Observarium.builder()`)

All configuration for a plain Java setup goes through the fluent builder returned by `Observarium.builder()`. Each method returns the builder for chaining. Call `.build()` to obtain the immutable `Observarium` instance.

| Method | Type | Default | Description |
|---|---|---|---|
| `apiKey(String)` | `String` | `null` | Reserved for future use. Some posting-service implementations may read this value from `ObservariumConfig` at runtime. |
| `scrubLevel(ScrubLevel)` | `ScrubLevel` | `BASIC` | Controls which PII patterns are active. See [Scrub Levels](#scrub-levels) below. |
| `addScrubPattern(Pattern)` | `java.util.regex.Pattern` | — | Adds a single compiled regex to the active set. Each match is replaced with `[REDACTED]`. Can be called multiple times. |
| `fingerprinter(ExceptionFingerprinter)` | `ExceptionFingerprinter` | `DefaultExceptionFingerprinter` | Replaces the built-in fingerprinter. See [Custom Fingerprinter](#custom-fingerprinter). |
| `scrubber(DataScrubber)` | `DataScrubber` | `DefaultDataScrubber` | Replaces the built-in scrubber entirely. When set, `scrubLevel` and `addScrubPattern` are ignored. See [Custom Scrubber](#custom-scrubber). |
| `traceContextProvider(TraceContextProvider)` | `TraceContextProvider` | `MdcTraceContextProvider` | Replaces the MDC-based trace reader. See [Custom TraceContextProvider](#custom-tracecontextprovider). |
| `addPostingService(PostingService)` | `PostingService` | — | Appends a posting service to the list. Can be called multiple times. |
| `postingServices(List<PostingService>)` | `List<PostingService>` | — | Replaces the entire posting service list at once. |
| `queueCapacity(int)` | `int` | `256` | Capacity of the bounded `ArrayBlockingQueue` that backs the single background worker thread. When the queue is full, new events are dropped and a warning is logged. |

**Minimum working example:**

```java
Observarium obs = Observarium.builder()
    .addPostingService(new GitHubPostingService("owner/repo", "ghp_token"))
    .build();
```

---

## Spring Boot Properties

All properties are under the `observarium` prefix. Use either `application.yml` or `application.properties`.

| Property | Type | Default | Description |
|---|---|---|---|
| `observarium.scrub-level` | `NONE \| BASIC \| STRICT` | `BASIC` | PII scrub level applied to messages and stack traces. |
| `observarium.queue-capacity` | `int` | `256` | Bounded queue capacity for the async worker. |
| `observarium.install-uncaught-handler` | `boolean` | `true` | When `true`, calls `ObservariumExceptionHandler.install()` on application startup, reporting uncaught exceptions as `FATAL`. |
| `observarium.github.repository` | `String` | — | GitHub repository in `owner/repo` format. Enables the GitHub posting service when present. |
| `observarium.github.token` | `String` | — | GitHub personal access token or fine-grained token. |
| `observarium.jira.base-url` | `String` | — | Jira instance URL, e.g. `https://myorg.atlassian.net`. Enables the Jira posting service when present. |
| `observarium.jira.project-key` | `String` | — | Jira project key, e.g. `OPS`. |
| `observarium.jira.email` | `String` | — | Jira account email for Basic Auth. |
| `observarium.jira.api-token` | `String` | — | Jira API token. |
| `observarium.gitlab.base-url` | `String` | — | GitLab instance URL, e.g. `https://gitlab.com`. Enables the GitLab posting service when present. |
| `observarium.gitlab.project-id` | `String` | — | GitLab numeric project ID or `namespace/project` path. |
| `observarium.gitlab.token` | `String` | — | GitLab personal access token or project access token. |
| `observarium.email.smtp-host` | `String` | — | SMTP server hostname. Enables the Email posting service when present. |
| `observarium.email.smtp-port` | `int` | `587` | SMTP server port. |
| `observarium.email.username` | `String` | — | SMTP authentication username. |
| `observarium.email.password` | `String` | — | SMTP authentication password. |
| `observarium.email.from` | `String` | — | Sender address. |
| `observarium.email.to` | `String` | — | Recipient address. |

**Example `application.yml`:**

```yaml
observarium:
  scrub-level: STRICT
  queue-capacity: 512
  install-uncaught-handler: true
  github:
    repository: acme/backend
    token: ${GITHUB_TOKEN}
  jira:
    base-url: https://acme.atlassian.net
    project-key: OPS
    email: ${JIRA_EMAIL}
    api-token: ${JIRA_TOKEN}
```

---

## Quarkus Properties

Identical keys to Spring Boot; use `application.properties` or `application.yaml`.

| Property | Type | Default | Description |
|---|---|---|---|
| `observarium.scrub-level` | `NONE \| BASIC \| STRICT` | `BASIC` | PII scrub level. |
| `observarium.queue-capacity` | `int` | `256` | Async queue capacity. |
| `observarium.install-uncaught-handler` | `boolean` | `true` | Install global uncaught exception handler at startup. |
| `observarium.github.repository` | `String` | — | `owner/repo`. Enables GitHub posting when present. |
| `observarium.github.token` | `String` | — | GitHub token. |
| `observarium.jira.base-url` | `String` | — | Jira base URL. Enables Jira posting when present. |
| `observarium.jira.project-key` | `String` | — | Jira project key. |
| `observarium.jira.email` | `String` | — | Jira account email. |
| `observarium.jira.api-token` | `String` | — | Jira API token. |
| `observarium.gitlab.base-url` | `String` | — | GitLab base URL. Enables GitLab posting when present. |
| `observarium.gitlab.project-id` | `String` | — | GitLab project ID or path. |
| `observarium.gitlab.token` | `String` | — | GitLab token. |
| `observarium.email.smtp-host` | `String` | — | SMTP host. Enables Email posting when present. |
| `observarium.email.smtp-port` | `int` | `587` | SMTP port. |
| `observarium.email.username` | `String` | — | SMTP username. |
| `observarium.email.password` | `String` | — | SMTP password. |
| `observarium.email.from` | `String` | — | Sender address. |
| `observarium.email.to` | `String` | — | Recipient address. |

**Example `application.properties`:**

```properties
observarium.scrub-level=STRICT
observarium.queue-capacity=512
observarium.install-uncaught-handler=true
observarium.github.repository=acme/backend
observarium.github.token=${GITHUB_TOKEN}
```

---

## Scrub Levels

The `ScrubLevel` enum controls which regular expressions `DefaultDataScrubber` applies to exception messages and full stack trace text. Every match is replaced with the literal string `[REDACTED]`.

### `NONE`

No patterns are applied. The raw exception message and stack trace are sent to the posting service unchanged.

Use this only in development environments where the data contains no production PII.

### `BASIC` (default)

Applies patterns that target credentials and tokens likely to appear in exception messages:

| Pattern | Example match |
|---|---|
| Key-value credentials | `password=hunter2`, `token: abc123`, `api_key=xyz` |
| Bearer tokens | `Bearer eyJhbGciO...` |

```
// Input:  "Connection failed: password=supersecret host=db.internal"
// Output: "Connection failed: [REDACTED] host=db.internal"
```

### `STRICT`

Applies all `BASIC` patterns plus patterns for personal data:

| Pattern | Example match |
|---|---|
| Email addresses | `user@example.com` |
| IPv4 addresses | `192.168.1.42` |
| Phone numbers (US format) | `555-867-5309`, `5558675309` |

```
// Input:  "User alice@example.com from 10.0.0.5 called support at 555-123-4567"
// Output: "User [REDACTED] from [REDACTED] called support at [REDACTED]"
```

---

## Custom Scrub Patterns

Additional patterns are applied after all built-in patterns at the active level. The replacement is always `[REDACTED]`.

```java
import java.util.regex.Pattern;

Observarium obs = Observarium.builder()
    // Redact internal order IDs: ORD-followed by digits
    .addScrubPattern(Pattern.compile("ORD-\\d+"))
    // Redact UUIDs
    .addScrubPattern(Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
        Pattern.CASE_INSENSITIVE))
    .addPostingService(...)
    .build();
```

Custom patterns are additive: they do not replace the built-in level's patterns.

---

## Custom Scrubber

To bypass `DefaultDataScrubber` entirely, implement `DataScrubber` and pass it to the builder with `.scrubber()`. When a custom scrubber is provided, `scrubLevel` and `addScrubPattern` are ignored.

```java
import io.hephaistos.observarium.scrub.DataScrubber;

public class MyDataScrubber implements DataScrubber {

    @Override
    public String scrub(String text) {
        if (text == null) {
            return null;
        }
        // Replace all digits with *
        return text.replaceAll("\\d", "*");
    }
}

Observarium obs = Observarium.builder()
    .scrubber(new MyDataScrubber())
    .addPostingService(...)
    .build();
```

---

## Custom Fingerprinter

`DefaultExceptionFingerprinter` computes a SHA-256 hash over the exception class name, every frame's `className#methodName`, and the class names of the full cause chain. Line numbers are excluded so the fingerprint is stable across minor refactors.

To override, implement `ExceptionFingerprinter`:

```java
import io.hephaistos.observarium.fingerprint.ExceptionFingerprinter;

public class TopFrameFingerprinter implements ExceptionFingerprinter {

    @Override
    public String fingerprint(Throwable throwable) {
        // Group by exception type and top frame only
        StackTraceElement top = throwable.getStackTrace().length > 0
            ? throwable.getStackTrace()[0]
            : null;
        String key = throwable.getClass().getName()
            + (top != null ? "#" + top.getClassName() + "." + top.getMethodName() : "");
        return Integer.toHexString(key.hashCode());
    }
}

Observarium obs = Observarium.builder()
    .fingerprinter(new TopFrameFingerprinter())
    .addPostingService(...)
    .build();
```

---

## Custom TraceContextProvider

`MdcTraceContextProvider` reads `trace_id` and `span_id` from SLF4J MDC. The default key names match what the OpenTelemetry Java Agent and most tracing bridges write to MDC.

Override the keys when your tracing library uses different names:

```java
import io.hephaistos.observarium.trace.MdcTraceContextProvider;

// Brave / Spring Cloud Sleuth uses "traceId" and "spanId"
Observarium obs = Observarium.builder()
    .traceContextProvider(new MdcTraceContextProvider("traceId", "spanId"))
    .addPostingService(...)
    .build();
```

Implement `TraceContextProvider` from scratch when MDC is not the right source:

```java
import io.hephaistos.observarium.trace.TraceContextProvider;
import io.opentelemetry.api.trace.Span;

public class OtelApiTraceContextProvider implements TraceContextProvider {

    @Override
    public String getTraceId() {
        Span span = Span.current();
        return span.getSpanContext().isValid()
            ? span.getSpanContext().getTraceId()
            : null;
    }

    @Override
    public String getSpanId() {
        Span span = Span.current();
        return span.getSpanContext().isValid()
            ? span.getSpanContext().getSpanId()
            : null;
    }
}

Observarium obs = Observarium.builder()
    .traceContextProvider(new OtelApiTraceContextProvider())
    .addPostingService(...)
    .build();
```

---

## Async Behaviour

`Observarium.captureException()` returns immediately with a `CompletableFuture<List<PostingResult>>`. The actual work (fingerprinting, scrubbing, HTTP calls to the issue tracker) executes on a single daemon background thread backed by an `ArrayBlockingQueue`.

Key properties:

- **Single worker thread** — events are processed in submission order, no concurrency within Observarium itself.
- **Bounded queue** — when the queue reaches `queueCapacity` (default 256), new events are dropped silently except for a `WARN` log line: `"Observarium queue full, dropping exception report"`. This protects the application from backpressure caused by a slow issue tracker.
- **Shutdown** — a JVM shutdown hook waits up to 10 seconds for the queue to drain, then calls `shutdownNow()`. Call `obs.shutdown()` explicitly if you need to trigger this earlier, for example in a `@PreDestroy` method.
- **Failure isolation** — if a posting service throws an unchecked exception, `ExceptionProcessor` catches it, logs it at `ERROR`, and returns a `PostingResult.failure(...)`. The application thread that called `captureException` is never affected.

```java
// Inspect results if you need to know the outcome
CompletableFuture<List<PostingResult>> future =
    obs.captureException(e, Severity.ERROR);

future.thenAccept(results ->
    results.forEach(r -> {
        if (r.success()) {
            System.out.println("Issue: " + r.url());
        } else {
            System.err.println("Failed: " + r.errorMessage());
        }
    })
);
```
