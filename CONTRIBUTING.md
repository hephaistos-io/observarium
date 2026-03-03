# Contributing

## Prerequisites

- Java 21+
- Gradle (wrapper included — use `./gradlew`)

## Setup

```sh
git clone https://github.com/hephaistos-io/observarium.git
cd observarium
./gradlew build
```

## Development

### Building the library

```sh
./gradlew build
```

Compiles all modules, runs tests, enforces code coverage, and produces JARs in each module's `build/libs/` directory.

### Project structure

```
observarium/
├── observarium-core/          # Core engine (zero framework deps)
├── observarium-spring-boot/   # Spring Boot auto-configuration
├── observarium-quarkus/       # Quarkus CDI extension
├── observarium-github/        # GitHub Issues posting service
├── observarium-jira/          # Jira Cloud posting service
├── observarium-gitlab/        # GitLab Issues posting service
└── observarium-email/         # SMTP email posting service
```

All posting modules depend only on `observarium-core`, `java.net.http`, and Gson. The framework modules (`spring-boot`, `quarkus`) are optional thin integrations.

## Code quality

Run all checks at once:

```sh
./gradlew check
```

This runs the full pipeline for every module:

| Step | What it checks |
|---|---|
| `compileJava` | Compilation |
| `test` | Unit tests (JUnit 5) |
| `jacocoTestReport` | Coverage report generation |
| `jacocoTestCoverageVerification` | Coverage >= 80% instruction coverage (fails the build if not met) |

### Coverage reports

After running tests, HTML coverage reports are available at:

```
<module>/build/reports/jacoco/test/html/index.html
```

## Testing

```sh
./gradlew test
```

Runs the JUnit 5 test suite across all modules. Tests live under `src/test/java/` in each module.

To run tests for a specific module:

```sh
./gradlew :observarium-core:test
./gradlew :observarium-github:test
```

### Writing tests

- Use JUnit 5 (`org.junit.jupiter`)
- Prefer simple stub/fake implementations over mocking frameworks where possible
- Mockito is available in modules that use HTTP clients (`observarium-github`, `observarium-jira`, `observarium-gitlab`)
- All new code must maintain >= 80% instruction coverage in its module

## AI-assisted contributions

AI-assisted pull requests are welcome. If you use AI tools (Claude, Copilot, ChatGPT, etc.) to help write your contribution, please:

1. **Disclose it** — state clearly in the PR description which parts were AI-assisted
2. **Review it yourself** — every line of AI-generated code must be reviewed and understood by a human before submitting
3. **Own it** — you are responsible for the correctness, security, and quality of the code regardless of how it was produced

PRs that appear to be unreviewed AI output (no human context, no understanding of the changes when asked) may be closed.
