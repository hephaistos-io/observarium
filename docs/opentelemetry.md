# OpenTelemetry Integration

Observarium correlates exceptions with distributed traces by reading trace context from SLF4J MDC. This design means there is zero compile-time dependency between `observarium-core` and any OpenTelemetry library. Any tracing setup that writes `trace_id` and `span_id` to MDC works automatically.

---

## How It Works

When `captureException()` is called, `ExceptionProcessor` calls `TraceContextProvider.getTraceId()` and `TraceContextProvider.getSpanId()` on the thread that submitted the exception. The default implementation, `MdcTraceContextProvider`, reads from SLF4J MDC using the keys `trace_id` and `span_id`.

If both values are `null`, the issue body simply omits the trace fields. If either value is present, `IssueFormatter.markdownBody()` includes them:

```markdown
**Trace ID:** `4bf92f3577b34da6a3ce929d0e0e4736`
**Span ID:** `00f067aa0ba902b7`
```

Because Observarium processes events on a single background worker thread, the MDC values are captured eagerly at submission time (on the caller's thread) and stored in the `ExceptionEvent`. The worker thread does not need access to the original MDC.

---

## Setup with the OpenTelemetry Java Agent

The [OpenTelemetry Java Agent](https://opentelemetry.io/docs/instrumentation/java/automatic/) automatically propagates trace context to SLF4J MDC using the keys `trace_id` and `span_id`. No additional configuration is needed beyond running the agent.

Start your application with the agent attached:

```bash
java -javaagent:opentelemetry-javaagent.jar \
     -Dotel.service.name=my-service \
     -Dotel.exporter.otlp.endpoint=http://localhost:4317 \
     -jar myapp.jar
```

With the agent running, any exception captured inside an active span will automatically have trace and span IDs attached to the Observarium issue.

Observarium uses the MDC keys `trace_id` and `span_id`, which are the defaults written by the OTel agent. No custom `TraceContextProvider` is needed.

---

## Setup with Spring Boot and Micrometer Tracing

Spring Boot 3.x uses [Micrometer Tracing](https://micrometer.io/docs/tracing) as its tracing facade. When used with the OpenTelemetry bridge (`micrometer-tracing-bridge-otel`), Micrometer Tracing writes trace context to MDC.

The MDC keys written by Micrometer Tracing are `traceId` and `spanId` (camel case, no underscore).

### 1. Add dependencies

```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

### 2. Configure the custom MDC key names

Because Micrometer Tracing uses `traceId` / `spanId` instead of `trace_id` / `span_id`, tell Observarium to use the Micrometer key names:

**Spring Boot (`application.yml`)**

```yaml
observarium:
  trace-id-mdc-key: traceId
  span-id-mdc-key: spanId
```

**Plain Java builder**

```java
import io.hephaistos.observarium.trace.MdcTraceContextProvider;

Observarium obs = Observarium.builder()
    .traceContextProvider(new MdcTraceContextProvider("traceId", "spanId"))
    .addPostingService(...)
    .build();
```

### 3. Configure Spring Boot tracing

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
```

With this setup, any exception captured inside a Spring-managed request or scheduled task will include the active trace ID.

---

## Setup with Quarkus OpenTelemetry

Quarkus has built-in OpenTelemetry support via the `quarkus-opentelemetry` extension. Quarkus writes `traceId` and `spanId` to MDC (camel case).

### 1. Add the Quarkus extension

```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-opentelemetry</artifactId>
</dependency>
```

### 2. Configure the MDC key names

```properties
# application.properties
observarium.trace-id-mdc-key=traceId
observarium.span-id-mdc-key=spanId

# Quarkus OTel exporter
quarkus.otel.exporter.otlp.traces.endpoint=http://localhost:4317
```

**Plain Java / Quarkus builder override**

```java
Observarium obs = Observarium.builder()
    .traceContextProvider(new MdcTraceContextProvider("traceId", "spanId"))
    .addPostingService(...)
    .build();
```

---

## Custom MDC Keys

`MdcTraceContextProvider` accepts arbitrary MDC key names via its two-argument constructor. Use this when your tracing library writes keys that differ from both the OTel agent defaults and the Micrometer defaults.

```java
// Brave (used by older Spring Cloud Sleuth) writes "X-B3-TraceId" / "X-B3-SpanId"
// but its MDC bridge typically uses "traceId" / "spanId".
// Check your specific bridge library's documentation.

new MdcTraceContextProvider("traceId", "spanId")     // Brave / Micrometer
new MdcTraceContextProvider("trace_id", "span_id")   // OTel Java Agent (default)
new MdcTraceContextProvider("dd.trace_id", "dd.span_id") // Datadog APM agent
```

---

## How Traces Appear in Issues

When trace context is available, the generated issue body includes the IDs in the header section, immediately after the thread name:

```markdown
<!-- observarium:fingerprint:a3f8c2d1... -->

## Exception

**Type:** `com.example.PaymentException`

**Message:** Payment gateway timeout

**Severity:** ERROR

**Timestamp:** 2026-03-03T14:32:01.123456Z

**Thread:** http-nio-8080-exec-3

**Trace ID:** `4bf92f3577b34da6a3ce929d0e0e4736`

**Span ID:** `00f067aa0ba902b7`

## Stack Trace

```
com.example.PaymentException: Payment gateway timeout
    at com.example.PaymentService.charge(PaymentService.java:87)
    ...
```

**Fingerprint:** `a3f8c2d1...`
```

The trace ID can be copy-pasted directly into your observability platform (Jaeger, Tempo, Zipkin, Datadog, etc.) to jump to the exact trace that produced the exception.

Recurrence comments produced by `IssueFormatter.markdownComment()` also include the trace and span IDs, so you can correlate each individual occurrence with its own trace.

---

## Implementing a Non-MDC TraceContextProvider

If your setup does not use MDC — for example you integrate directly with the OpenTelemetry API — implement `TraceContextProvider` directly. See the [Configuration Reference](configuration.md#custom-tracecontextprovider) for a full example using `Span.current()`.
