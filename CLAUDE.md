# Observarium

Exception tracking library for Java 21. Zero infrastructure — captures exceptions, deduplicates via fingerprint, scrubs PII, posts to issue trackers.

## Project structure

Multi-module Gradle project (`settings.gradle`):

- `observarium-core` — core engine (fingerprinting, scrubbing, async dispatch, `PostingService` SPI)
- `observarium-spring-boot` — Spring Boot auto-configuration
- `observarium-quarkus` — Quarkus CDI extension
- `observarium-github`, `observarium-jira`, `observarium-gitlab`, `observarium-email` — posting service implementations

Package root: `io.hephaistos.observarium`

## Build and test

- Java 21, Gradle wrapper included
- `./gradlew build` — full build with Spotless formatting, PMD, tests, JaCoCo (80% threshold)
- `./gradlew test` — tests only
- `./gradlew spotlessApply` — auto-fix formatting
- `./gradlew pmdMain` — static analysis

## Code style

- Google Java Format (enforced by Spotless)
- PMD ruleset: `config/pmd/ruleset.xml`

## Key design constraints

- Core module has no framework dependencies — plain Java builder API
- Posting modules use only `java.net.http.HttpClient` + Gson — no platform SDKs
- The library must never disrupt the host application: all failures are caught and logged, never thrown

## Further reading

- [README.md](README.md) — architecture diagram, quick start, module overview
- [docs/](docs/) — full documentation (config reference, posting services, custom integrations, OpenTelemetry)
