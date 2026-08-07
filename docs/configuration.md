# Configuration Reference

---

## Builder API (`Observarium.builder()`)

All configuration for a plain Java setup goes through the fluent builder returned by `Observarium.builder()`. Each method returns the builder for chaining. Call `.build()` to obtain the immutable `Observarium` instance.

| Method | Type | Default | Description |
|---|---|---|---|
| `scrubLevel(ScrubLevel)` | `ScrubLevel` | `BASIC` | Controls which PII patterns are active. See [Scrub Levels](#scrub-levels) below. |
| `addScrubPattern(Pattern)` | `java.util.regex.Pattern` | — | Adds a single compiled regex to the active set. Each match is replaced with `[REDACTED]`. Can be called multiple times. |
| `fingerprinter(ExceptionFingerprinter)` | `ExceptionFingerprinter` | `DefaultExceptionFingerprinter` | Replaces the built-in fingerprinter. See [Custom Fingerprinter](#custom-fingerprinter). |
| `scrubber(DataScrubber)` | `DataScrubber` | `DefaultDataScrubber` | Replaces the built-in scrubber entirely. When set, `scrubLevel` and `addScrubPattern` are ignored. See [Custom Scrubber](#custom-scrubber). |
| `traceContextProvider(TraceContextProvider)` | `TraceContextProvider` | `MdcTraceContextProvider` | Replaces the MDC-based trace reader. See [Custom TraceContextProvider](#custom-tracecontextprovider). |
| `addPostingService(PostingService)` | `PostingService` | — | Appends a posting service to the list. Can be called multiple times. |
| `postingServices(List<PostingService>)` | `List<PostingService>` | — | Replaces the entire posting service list at once. |
| `listener(ObservariumListener)` | `ObservariumListener` | no-op | Registers a lifecycle listener that receives callbacks for exception captures, drops, and posting outcomes. Used by `observarium-micrometer` to bridge events to Micrometer meters. See [ObservariumListener](#observariumlistener). |
| `queueCapacity(int)` | `int` | `256` | Capacity of the bounded `ArrayBlockingQueue` that backs the single background worker thread. When the queue is full, new events are dropped and a warning is logged. |
| `maxDuplicateComments(int)` | `int` | `5` | Maximum number of duplicate comments posted on a single existing issue before further recurrences are dropped silently. Use `-1` for unlimited. See [Duplicate Comment Limit](#duplicate-comment-limit). |
| `ignoreIf(Predicate<Throwable>)` | `java.util.function.Predicate<Throwable>` | never ignores | Suppresses reporting for a throwable the predicate matches. Runs before fingerprinting, scrubbing, and the queue — an ignored throwable costs nothing beyond the predicate check. Can be called multiple times; a throwable is ignored if **any** registered predicate matches (combined with logical `OR`). See [Exception Filtering](#exception-filtering). |
| `shutdownTimeout(Duration)` | `java.time.Duration` | `10s` | Maximum time `shutdown()` (and the JVM shutdown hook) blocks draining in-flight work before forcing a shutdown and closing posting services. See [Shutdown Budget](#shutdown-budget). |

**Minimum working example:**

```java
Observarium obs = Observarium.builder()
    .addPostingService(new GitHubPostingService(
        GitHubConfig.of("ghp_token", "owner", "repo")))
    .build();
```

---

## Spring Boot Properties

All properties are under the `observarium` prefix. Use either `application.yml` or `application.properties`.

| Property | Type | Default | Description |
|---|---|---|---|
| `observarium.scrub-level` | `NONE \| BASIC \| STRICT` | `BASIC` | PII scrub level applied to messages and stack traces. |
| `observarium.github.enabled` | `boolean` | `false` | Enable the GitHub posting service. |
| `observarium.github.token` | `String` | — | GitHub personal access token or fine-grained token. |
| `observarium.github.owner` | `String` | — | GitHub repository owner (organization name or user login). |
| `observarium.github.repo` | `String` | — | GitHub repository name. |
| `observarium.github.label-prefix` | `String` | `observarium` | Label applied to all issues created by Observarium. |
| `observarium.jira.enabled` | `boolean` | `false` | Enable the Jira posting service. |
| `observarium.jira.base-url` | `String` | — | Jira instance URL, e.g. `https://myorg.atlassian.net`. |
| `observarium.jira.username` | `String` | — | Jira account username (email address for Jira Cloud). |
| `observarium.jira.api-token` | `String` | — | Jira API token. |
| `observarium.jira.project-key` | `String` | — | Jira project key, e.g. `OPS`. |
| `observarium.jira.issue-type` | `String` | `Bug` | Jira issue type name for created issues. |
| `observarium.gitlab.enabled` | `boolean` | `false` | Enable the GitLab posting service. |
| `observarium.gitlab.base-url` | `String` | — | GitLab instance URL, e.g. `https://gitlab.com`. |
| `observarium.gitlab.private-token` | `String` | — | GitLab personal access token or project access token. |
| `observarium.gitlab.project-id` | `String` | — | GitLab numeric project ID or `namespace/project` path. |
| `observarium.email.enabled` | `boolean` | `false` | Enable the Email posting service. |
| `observarium.email.smtp-host` | `String` | — | SMTP server hostname. |
| `observarium.email.smtp-port` | `int` | `587` | SMTP server port. |
| `observarium.email.from` | `String` | — | Sender address. |
| `observarium.email.to` | `String` | — | Recipient address. |
| `observarium.email.username` | `String` | — | SMTP authentication username. |
| `observarium.email.password` | `String` | — | SMTP authentication password. |
| `observarium.email.auth` | `boolean` | `true` | Enable SMTP authentication. |
| `observarium.email.start-tls` | `boolean` | `true` | Enable STARTTLS. |
| `observarium.max-duplicate-comments` | `int` | `5` | Maximum number of duplicate comments posted on a single existing issue. Use `-1` for unlimited. See [Duplicate Comment Limit](#duplicate-comment-limit). |
| `observarium.queue-capacity` | `int` | `256` | Capacity of the bounded queue backing the background worker. Bound to `Builder#queueCapacity`. |
| `observarium.scrub-patterns` | `List<String>` | empty | Additional regex patterns, one per entry, forwarded to `Builder#addScrubPattern`. Each pattern is compiled while binding configuration properties, so an invalid regex fails application startup with the offending pattern named in the error, rather than only surfacing later as a log line on the background worker thread. |
| `observarium.ignored-exceptions` | `List<String>` | empty | Fully-qualified exception class names to ignore. A captured throwable is ignored when its class, or any superclass or implemented interface, matches one of these names — listing a supertype also suppresses its subclasses. Composed via `Builder#ignoreIf`. See [Exception Filtering](#exception-filtering). |
| `observarium.shutdown-timeout` | `Duration` | `10s` | Maximum time the shutdown drain may take. Accepts Spring's simple duration syntax (e.g. `30s`, `500ms`) or ISO-8601 (`PT30S`). Bound to `Builder#shutdownTimeout`. See [Shutdown Budget](#shutdown-budget). |

Spring Boot additionally ignores framework-classified client errors **by default**, with no property needed: `ErrorResponse` implementations, any exception annotated `@ResponseStatus` with a `4xx` status, and `BindException` (which covers `MethodArgumentNotValidException`, since it is a subclass). This default predicate is combined with `observarium.ignored-exceptions` via the same `OR` semantics described in [Exception Filtering](#exception-filtering) — either one matching is enough to suppress reporting. It only activates when Spring MVC (`spring-web`) is present on the runtime classpath, so a non-web Spring Boot application that depends on `observarium-spring-boot` without `spring-web` is unaffected.

**Example `application.yml`:**

```yaml
observarium:
  scrub-level: STRICT
  github:
    owner: acme
    repo: backend
    token: ${GITHUB_TOKEN}
  jira:
    base-url: https://acme.atlassian.net
    username: ${JIRA_USERNAME}
    api-token: ${JIRA_TOKEN}
    project-key: OPS
```

---

## Quarkus Properties

Identical keys to Spring Boot; use `application.properties` or `application.yaml`.

The Quarkus module uses the same property names as the Spring Boot module, including `observarium.max-duplicate-comments`, `observarium.queue-capacity`, `observarium.scrub-patterns`, `observarium.ignored-exceptions`, and `observarium.shutdown-timeout`. Refer to the Spring Boot table above for the complete list. `observarium.shutdown-timeout` accepts either the ISO-8601 duration format (`PT30S`) or the shorthand SmallRye Config also understands (`30s`).

Unlike Spring Boot, the Quarkus module does not ignore any exceptions by default — `observarium.ignored-exceptions` is the only filtering knob, since there is no equivalent framework-classified "client error" concept wired in for Quarkus. An invalid regex in `observarium.scrub-patterns` fails eagerly when the `Observarium` CDI bean is produced, naming the offending pattern, the same as in Spring Boot.

**Example `application.properties`:**

```properties
observarium.scrub-level=STRICT
observarium.github.owner=acme
observarium.github.repo=backend
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

## Exception Filtering

`ignoreIf` suppresses reporting entirely for a matching throwable — it is the cheapest possible outcome, since it short-circuits `captureException` before fingerprinting, scrubbing, or the exception ever taking a slot on the background queue.

```java
Observarium obs = Observarium.builder()
    .ignoreIf(t -> t instanceof java.io.FileNotFoundException)
    .addPostingService(...)
    .build();
```

**Composition:** calling `ignoreIf` more than once is additive. A throwable is ignored if **any** registered predicate matches — the predicates are combined with logical `OR`, not `AND`. Each call registers an independent reason to ignore; the exception is suppressed as soon as one of them fires. This is also how the Spring and Quarkus integrations layer their own defaults on top of user configuration: Spring's built-in "ignore client errors" predicate and the `observarium.ignored-exceptions` list are two separate `ignoreIf` registrations, so either one matching is enough.

**Failure handling:** a predicate that throws is treated as "not ignored" (fail open) — the throwable is still reported, and the predicate's own exception is logged at `DEBUG` and swallowed. This means a bug in a custom filter can never accidentally silence real defects.

### Spring Boot and Quarkus

`observarium.ignored-exceptions` takes a list of fully-qualified class names. A captured throwable is ignored when its class, or any superclass or implemented interface, matches one of the listed names — so listing a shared domain base exception also covers every subclass without enumerating them individually.

```yaml
observarium:
  ignored-exceptions:
    - com.acme.orders.OrderNotFoundException
    - com.acme.auth.AccessDeniedException
```

In Spring Boot specifically, this list is combined (via the same `OR` semantics) with a built-in default that ignores whatever Spring MVC already classifies as a client error: `ErrorResponse` implementations, any exception annotated `@ResponseStatus` with a `4xx` status, and `BindException` (which covers `MethodArgumentNotValidException`). A `4xx` is the API contract working as intended, not a defect — without this default, every new adopter's first experience with the auto-configured MVC advice is a tracker full of validation errors. This default only activates when `spring-web` is present on the runtime classpath.

---

## Shutdown Budget

`shutdownTimeout` bounds how long `shutdown()` — and the JVM shutdown hook that runs it automatically at exit — will wait for in-flight work to drain before forcing a shutdown and closing posting services. It defaults to 10 seconds, matching the library's historical (previously hardcoded) behavior.

```java
Observarium obs = Observarium.builder()
    .shutdownTimeout(Duration.ofSeconds(20))
    .addPostingService(...)
    .build();
```

### Spring Boot: overlapping with the web server's graceful shutdown

`observarium-spring-boot` drains the queue from a `SmartLifecycle` bean rather than a bean `destroyMethod`. This matters because bean destruction (`destroySingletons()`) always runs *after every* `SmartLifecycle` bean has finished stopping — including Spring Boot's own `WebServerGracefulShutdownLifecycle`, which drains in-flight HTTP requests. A `destroyMethod`-based drain would therefore always run **after** the request drain, adding its own timeout on top rather than overlapping it.

Observarium's `SmartLifecycle` bean shares `WebServerGracefulShutdownLifecycle`'s exact phase. Spring's `DefaultLifecycleProcessor` groups `SmartLifecycle` beans by phase and stops every bean within a phase **concurrently**, each on its own thread, waiting for the whole phase to finish before moving on. Sharing the phase means the two drains run side by side, so the wall-clock cost of shutting down the application is:

```
max(request drain, observarium drain)
```

rather than their sum. When sizing a deployment's termination grace period, budget for whichever of `spring.lifecycle.timeout-per-shutdown-phase` (covering the request drain) and `observarium.shutdown-timeout` is larger — not both added together.

```yaml
observarium:
  shutdown-timeout: 15s

spring:
  lifecycle:
    timeout-per-shutdown-phase: 20s
```

With the configuration above, closing the application context with in-flight requests and a full Observarium queue completes in at most 20 seconds (the larger of the two), not 35.

---

## Async Behaviour

`Observarium.captureException()` returns immediately with a `CompletableFuture<List<PostingResult>>`. The actual work (fingerprinting, scrubbing, HTTP calls to the issue tracker) executes on a single daemon background thread backed by an `ArrayBlockingQueue`.

Key properties:

- **Single worker thread** — events are processed in submission order, no concurrency within Observarium itself.
- **Bounded queue** — when the queue reaches `queueCapacity` (default 256), new events are dropped silently except for a `WARN` log line: `"Observarium queue full, dropping exception report"`. This protects the application from backpressure caused by a slow issue tracker.
- **Shutdown** — both the JVM shutdown hook and `obs.shutdown()` wait up to `shutdownTimeout` (default 10 seconds, see [Shutdown Budget](#shutdown-budget)) for in-flight work to complete, then force shutdown only if the drain times out, and then close all posting services. `obs.shutdown()` blocks for the duration of this sequence. Call it explicitly when you need to stop processing before JVM exit, for example in a `@PreDestroy` method.
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

---

## ObservariumListener

`ObservariumListener` is a callback interface in `observarium-core` that lets you observe the internal lifecycle of the processing pipeline without modifying core logic. All methods have no-op defaults; implement only the events you care about.

| Method | Called when | Thread |
|---|---|---|
| `onExceptionCaptured(Severity)` | An exception is successfully enqueued | Caller's thread |
| `onExceptionDropped()` | An exception is dropped because the queue is full | Caller's thread |
| `onPostingCompleted(serviceName, duplicate, success, durationNanos)` | A posting service finishes processing | Background worker thread |
| `onQueueSizeAvailable(Supplier<Integer>)` | The `Observarium` instance is constructed; provides a live queue-depth supplier | Construction thread |

Implementations must be thread-safe and must not throw. Any exception thrown from a callback is caught and logged but otherwise ignored.

Register a listener via the builder:

```java
Observarium obs = Observarium.builder()
    .listener(new MyObservariumListener())
    .addPostingService(...)
    .build();
```

The primary built-in use of this interface is `ObservariumMeterBinder` from `observarium-micrometer`, which bridges these callbacks to Micrometer meters. See [Micrometer Integration](micrometer.md) for setup details.

---

## Duplicate Comment Limit

When an exception recurs frequently, Observarium caps the number of duplicate comments posted on an existing issue to prevent issue tracker noise.

### Behaviour by threshold

For each duplicate occurrence, `ExceptionProcessor` retrieves the current comment count from `DuplicateSearchResult` and compares it against `maxDuplicateComments`:

| Condition | Action |
|---|---|
| `commentCount < maxDuplicateComments` | Normal `commentOnIssue` call — the recurrence is appended to the issue. |
| `commentCount == maxDuplicateComments` | `postCommentLimitNotice` is called once — a final "Comment Limit Reached" notice is posted on the issue. |
| `commentCount > maxDuplicateComments` | The occurrence is dropped silently. `observarium.comments.dropped` counter is incremented. `ObservariumListener.onCommentDropped(serviceName)` is called. |

> **Note:** The notice itself is an additional comment, so the total number of comments Observarium may post is `maxDuplicateComments + 1` (N regular comments plus the final notice). For example, with `maxDuplicateComments=5`, up to 6 comments may appear on the issue: 5 duplicate occurrence comments and 1 limit notice.

### Comment count source

The comment count is read from the tracker API during `findDuplicate()`:

| Backend | API field |
|---|---|
| GitHub | `comments` field on the issue JSON |
| GitLab | `user_notes_count` field on the issue JSON |
| Jira | `fields.comment.total` from the issue response |

The count reflects **all comments on the issue**, not just those posted by Observarium. This means comments left by human users, bots, or other integrations also count toward the limit. This is intentional: if an issue already has significant discussion, additional automated noise is unwanted regardless of who posted the existing comments.

> **GitLab caveat:** GitLab's `user_notes_count` includes system-generated notes (label changes, milestone updates, etc.) in addition to user comments. This means the limit may trigger earlier than expected on issues with frequent label or milestone activity.

### Custom posting services and fail-open behaviour

`DuplicateSearchResult.found(id, url)` (the 2-argument form) returns `COMMENT_COUNT_UNKNOWN = -1` for the comment count. When `ExceptionProcessor` sees `-1`, it treats the count as below the limit and always allows the comment through. This means custom `PostingService` implementations that have not been updated to return a comment count continue to work without restriction. See [Custom Posting Service](custom-posting-service.md#duplicatesearchresult) for how to supply the count.

### Configuration example

**Plain Java builder:**

```java
Observarium obs = Observarium.builder()
    .maxDuplicateComments(10)          // cap at 10 comments per issue
    // .maxDuplicateComments(-1)       // unlimited
    .addPostingService(new GitHubPostingService(GitHubConfig.of(token, "owner", "repo")))
    .build();
```

**Spring Boot / Quarkus property:**

```properties
observarium.max-duplicate-comments=10
```
