# Getting Started

## Prerequisites

- Java 21 or later
- Gradle or Maven build tool
- Access credentials for at least one supported issue tracker (GitHub, Jira, GitLab) or an SMTP server

---

## Plain Java

This approach works in any Java application: command-line tools, servlet containers, embedded systems, or any framework not listed below.

### 1. Add dependencies

**Gradle (`build.gradle`)**

```groovy
dependencies {
    implementation 'io.hephaistos:observarium-core:0.9.0'
    // pick one or more posting service backends
    implementation 'io.hephaistos:observarium-github:0.9.0'
}
```

**Maven (`pom.xml`)**

```xml
<dependencies>
  <dependency>
    <groupId>io.hephaistos</groupId>
    <artifactId>observarium-core</artifactId>
    <version>0.9.0</version>
  </dependency>
  <dependency>
    <groupId>io.hephaistos</groupId>
    <artifactId>observarium-github</artifactId>
    <version>0.9.0</version>
  </dependency>
</dependencies>
```

### 2. Build an `Observarium` instance

Create exactly one instance per application and keep it for the lifetime of the process. The builder sets all defaults; only `.addPostingService()` is required for useful behaviour.

```java
import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.event.Severity;
import io.hephaistos.observarium.handler.ObservariumExceptionHandler;
import io.hephaistos.observarium.scrub.ScrubLevel;

import java.util.Map;
import java.util.regex.Pattern;

public class App {

    public static void main(String[] args) {

        Observarium obs = Observarium.builder()
            // Scrub passwords, tokens, Bearer headers, emails, IPs, phone numbers
            .scrubLevel(ScrubLevel.STRICT)
            // Optionally add project-specific patterns on top of the built-in ones
            .addScrubPattern(Pattern.compile("order-\\d{6,}"))
            // The async dispatch queue size; default is 256
            .queueCapacity(512)
            // Register one or more posting services
            .addPostingService(new GitHubPostingService(
                GitHubConfig.of("ghp_yourtoken", "owner", "repo")))
            .build();

        // Install as the JVM-wide uncaught exception handler.
        // Any thread that throws without catching will have its exception
        // reported as Severity.FATAL and the previous handler (if any) is
        // still called afterward.
        ObservariumExceptionHandler.install(obs);

        // Explicit capture — returns a CompletableFuture you can ignore or inspect
        try {
            riskyOperation();
        } catch (Exception e) {
            obs.captureException(e);
            // or with an explicit severity:
            obs.captureException(e, Severity.WARNING);
            // or with structured tags that appear in the issue body:
            obs.captureException(e, Severity.ERROR, Map.of(
                "user.id", "u-42",
                "tenant",  "acme"
            ));
        }
    }
}
```

### 3. Shutdown

The `Observarium` constructor registers a JVM shutdown hook that drains the queue (up to 10 seconds) and then calls `shutdownNow()`. You do not need to call `obs.shutdown()` manually unless you want explicit lifecycle control, for example in a test.

---

## Spring Boot

The `observarium-spring-boot` module provides auto-configuration. Drop it on the classpath together with one or more posting-service modules and configure via `application.yml` or `application.properties`.

### 1. Add dependencies

**Maven**

```xml
<dependencies>
  <dependency>
    <groupId>io.hephaistos</groupId>
    <artifactId>observarium-spring-boot</artifactId>
    <version>0.9.0</version>
  </dependency>
  <dependency>
    <groupId>io.hephaistos</groupId>
    <artifactId>observarium-github</artifactId>
    <version>0.9.0</version>
  </dependency>
</dependencies>
```

**Gradle**

```groovy
dependencies {
    implementation 'io.hephaistos:observarium-spring-boot:0.9.0'
    implementation 'io.hephaistos:observarium-github:0.9.0'
}
```

### 2. Configure via `application.yml`

```yaml
observarium:
  scrub-level: STRICT          # NONE | BASIC (default) | STRICT
  github:
    owner: owner
    repo: repo
    token: ${GITHUB_TOKEN}     # inject from environment variable
```

The auto-configuration creates an `Observarium` bean.

### 3. Capture exceptions in your code

Inject `Observarium` wherever you need it:

```java
import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.event.Severity;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final Observarium observarium;

    public OrderService(Observarium observarium) {
        this.observarium = observarium;
    }

    public void processOrder(String orderId) {
        try {
            // ...
        } catch (Exception e) {
            observarium.captureException(e, Severity.ERROR, Map.of("order.id", orderId));
            throw e;
        }
    }
}
```

---

## Quarkus

The `observarium-quarkus` module provides a CDI extension. It mirrors the Spring Boot integration in structure.

### 1. Add dependencies

**Maven**

```xml
<dependencies>
  <dependency>
    <groupId>io.hephaistos</groupId>
    <artifactId>observarium-quarkus</artifactId>
    <version>0.9.0</version>
  </dependency>
  <dependency>
    <groupId>io.hephaistos</groupId>
    <artifactId>observarium-github</artifactId>
    <version>0.9.0</version>
  </dependency>
</dependencies>
```

**Gradle**

```groovy
dependencies {
    implementation 'io.hephaistos:observarium-quarkus:0.9.0'
    implementation 'io.hephaistos:observarium-github:0.9.0'
}
```

### 2. Configure via `application.properties`

```properties
observarium.scrub-level=STRICT
observarium.github.owner=owner
observarium.github.repo=repo
observarium.github.token=${GITHUB_TOKEN}
```

### 3. Inject and use

```java
import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.event.Severity;
import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PaymentService {

    @Inject
    Observarium observarium;

    public void charge(String transactionId) {
        try {
            // ...
        } catch (Exception e) {
            observarium.captureException(e, Severity.ERROR,
                Map.of("transaction.id", transactionId));
            throw e;
        }
    }
}
```

---

## Other Frameworks

Use the plain Java builder API directly. The `Observarium` class has no framework dependencies; only `observarium-core` and your chosen posting-service module need to be on the classpath. Wire the single `Observarium` instance through whatever DI mechanism your framework provides (Guice, Dagger, CDI, manual singleton, etc.).

See [Configuration Reference](configuration.md) for the complete builder API.
