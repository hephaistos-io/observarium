# Micrometer Integration

`observarium-micrometer` bridges Observarium's internal lifecycle events to [Micrometer](https://micrometer.io/) meters. It exposes counters, a gauge, and a timer so you can monitor Observarium in any Micrometer-compatible observability backend (Prometheus, Datadog, Grafana Cloud, etc.).

This module is a metrics facade only — it records numbers via Micrometer's API, which forwards them to whichever `MeterRegistry` backend is configured in your application. Observarium itself has no dependency on any specific metrics backend.

---

## Available Meters

| Meter name | Type | Tags | Description |
|---|---|---|---|
| `observarium.exceptions.captured` | Counter | `severity` (`error`, `warning`, `info`, `fatal`) | Total exceptions successfully enqueued for processing |
| `observarium.exceptions.dropped` | Counter | — | Exceptions dropped because the processing queue was full |
| `observarium.queue.size` | Gauge | — | Current number of exceptions in the processing queue |
| `observarium.posting.duration` | Timer | `service`, `action` (`create`/`comment`), `outcome` (`success`/`failure`) | Wall-clock time for each posting service call, including the duplicate search |

The `severity` tag value on `observarium.exceptions.captured` is the lowercase name of the `Severity` enum: `info`, `warning`, `error`, or `fatal`.

The `service` tag value on `observarium.posting.duration` is the string returned by `PostingService.name()` for each configured backend (e.g., `github`, `jira`, `gitlab`, `email`). Avoid configuring dynamic or user-controlled values as service names: each distinct tag combination creates a new meter that is never removed from the registry.

---

## Setup: Plain Java

Add both `observarium-micrometer` and `micrometer-core` to your dependencies, then construct `ObservariumMeterBinder`, register it with your `MeterRegistry`, and pass it to the `Observarium` builder.

**Gradle**

```groovy
dependencies {
    implementation 'io.hephaistos:observarium-core:0.9.1'
    implementation 'io.hephaistos:observarium-micrometer:0.9.1'
    implementation 'io.micrometer:micrometer-core:1.14.5'
    // add a registry backend, e.g.:
    // implementation 'io.micrometer:micrometer-registry-prometheus:1.14.5'
}
```

**Maven**

```xml
<dependency>
  <groupId>io.hephaistos</groupId>
  <artifactId>observarium-core</artifactId>
  <version>0.9.1</version>
</dependency>
<dependency>
  <groupId>io.hephaistos</groupId>
  <artifactId>observarium-micrometer</artifactId>
  <version>0.9.1</version>
</dependency>
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-core</artifactId>
  <version>1.14.5</version>
</dependency>
```

**Wiring**

`ObservariumMeterBinder` implements both `ObservariumListener` and Micrometer's `MeterBinder`. Wire the two together before building the `Observarium` instance:

```java
import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.micrometer.ObservariumMeterBinder;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

MeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

ObservariumMeterBinder meterBinder = new ObservariumMeterBinder();

// Register meters with the registry
meterBinder.bindTo(registry);

// Pass the binder as the listener so it receives pipeline callbacks
Observarium obs = Observarium.builder()
    .listener(meterBinder)
    .addPostingService(new GitHubPostingService(
        GitHubConfig.of("ghp_yourtoken", "owner", "repo")))
    .build();
```

The order matters: call `bindTo()` before `Observarium.builder().build()`, or at least before any exceptions are captured, so that the `droppedCounter` is registered before it could be incremented. The `observarium.queue.size` gauge and the `observarium.exceptions.captured` counter are safe to register in either order because they handle a late `bindTo` gracefully.

---

## Setup: Spring Boot

Add `observarium-spring-boot`, `observarium-micrometer`, and `micrometer-core` to your dependencies. No additional configuration is required.

**Gradle**

```groovy
dependencies {
    implementation 'io.hephaistos:observarium-spring-boot:0.9.1'
    implementation 'io.hephaistos:observarium-micrometer:0.9.1'
    // micrometer-core is pulled in transitively by spring-boot-starter-actuator
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    // pick one or more posting service backends
    implementation 'io.hephaistos:observarium-github:0.9.1'
}
```

**Maven**

```xml
<dependency>
  <groupId>io.hephaistos</groupId>
  <artifactId>observarium-spring-boot</artifactId>
  <version>0.9.1</version>
</dependency>
<dependency>
  <groupId>io.hephaistos</groupId>
  <artifactId>observarium-micrometer</artifactId>
  <version>0.9.1</version>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

When both `observarium-micrometer` and `micrometer-core` are on the classpath, `ObservariumAutoConfiguration` registers `ObservariumMeterBinder` as the `ObservariumListener` bean via an inner `@ConditionalOnClass` configuration class. Because `ObservariumMeterBinder` also implements `MeterBinder`, Spring Boot Actuator auto-discovers it and calls `bindTo()` automatically.

No properties need to be set. The meters appear in the Actuator metrics endpoint at `/actuator/metrics`.

**Verify**

```bash
curl http://localhost:8080/actuator/metrics/observarium.exceptions.captured
curl http://localhost:8080/actuator/metrics/observarium.exceptions.dropped
curl http://localhost:8080/actuator/metrics/observarium.queue.size
curl http://localhost:8080/actuator/metrics/observarium.posting.duration
```

To expose the metrics endpoint, add the following to `application.yml` if it is not already exposed:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: metrics, health, info
```

---

## Setup: Quarkus

Add `observarium-quarkus`, `observarium-micrometer`, and the Quarkus Micrometer extension to your dependencies. Then declare `ObservariumMeterBinder` as an `@ApplicationScoped` CDI bean.

**Gradle**

```groovy
dependencies {
    implementation 'io.hephaistos:observarium-quarkus:0.9.1'
    implementation 'io.hephaistos:observarium-micrometer:0.9.1'
    implementation 'io.quarkus:quarkus-micrometer'
    // pick one or more posting service backends
    implementation 'io.hephaistos:observarium-github:0.9.1'
}
```

**Maven**

```xml
<dependency>
  <groupId>io.hephaistos</groupId>
  <artifactId>observarium-quarkus</artifactId>
  <version>0.9.1</version>
</dependency>
<dependency>
  <groupId>io.hephaistos</groupId>
  <artifactId>observarium-micrometer</artifactId>
  <version>0.9.1</version>
</dependency>
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-micrometer</artifactId>
</dependency>
```

**CDI bean declaration**

Unlike Spring Boot, Quarkus does not support classpath-conditional beans in plain library JARs (its bean discovery is build-time via Jandex, not runtime). You need to declare `ObservariumMeterBinder` as a CDI bean so that the `ObservariumProducer` picks it up via `Instance<ObservariumListener>`:

```java
import io.hephaistos.observarium.micrometer.ObservariumMeterBinder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class ObservariumMetricsConfig {

    @Produces
    @ApplicationScoped
    public ObservariumMeterBinder observariumMeterBinder() {
        return new ObservariumMeterBinder();
    }
}
```

The return type must be `ObservariumMeterBinder` (not `ObservariumListener`) so that CDI exposes both bean types: `ObservariumListener` (resolved by `ObservariumProducer` via `Instance<ObservariumListener>`) and `MeterBinder` (auto-discovered by Quarkus Micrometer, which calls `bindTo()` automatically).

**Verify**

Quarkus exposes Micrometer metrics at `/q/metrics` by default when `quarkus-micrometer-registry-prometheus` is on the classpath. Filter for Observarium metrics:

```bash
curl http://localhost:8080/q/metrics | grep observarium
```

---

## Common Tags

To attach extra tags to every meter (e.g., application name or environment), use the overloaded constructor:

```java
import io.micrometer.core.instrument.Tags;

ObservariumMeterBinder binder = new ObservariumMeterBinder(
    Tags.of("application", "my-service", "env", "production"));
```

These tags are appended to all counters, timers, and gauges registered by this binder, in addition to the per-meter tags listed above. This is useful when you need Observarium-specific labels beyond Micrometer's global common tags.

In Spring Boot, override the default bean to supply custom tags:

```java
@Bean
public ObservariumMeterBinder observariumMeterBinder() {
    return new ObservariumMeterBinder(Tags.of("app", "my-service"));
}
```

---

## Cleanup on Shutdown

`ObservariumMeterBinder` implements `AutoCloseable`. Calling `close()` removes all registered meters from the registry and resets internal state. This is useful during context refreshes (e.g., Spring DevTools hot-reload) to avoid stale meters lingering in the registry.

Spring Boot automatically invokes `close()` on bean destruction. For plain Java usage, call `close()` explicitly when shutting down:

```java
binder.close();
```

After `close()`, listener callbacks are silently ignored until `bindTo()` is called again.

---

## Notes on Meter Cardinality

Each unique combination of tags creates one meter entry in the registry. For the meters in this module:

- `observarium.exceptions.captured` — one entry per `Severity` value: at most 4 (`info`, `warning`, `error`, `fatal`).
- `observarium.exceptions.dropped` — exactly one entry, no tags.
- `observarium.queue.size` — exactly one entry, no tags.
- `observarium.posting.duration` — one entry per combination of `service` × `action` × `outcome`. With two actions (`create`, `comment`) and two outcomes (`success`, `failure`), each posting service contributes at most 4 timer series. The total is bounded by the number of configured posting services multiplied by 4.

The total meter count is small and fixed at startup for any given configuration. There is no risk of cardinality explosion from normal use.
