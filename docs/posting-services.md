# Posting Services

Observarium dispatches each captured exception to every configured `PostingService`. The processor first calls `findDuplicate()` on the service; if a duplicate is found it calls `commentOnIssue()`, otherwise it calls `createIssue()`. Every call returns a `PostingResult` that carries a success flag, the external issue ID, and a URL.

## Custom Issue Formatting

Every posting service delegates title, body, and comment generation to an `IssueFormatter` interface. The built-in `DefaultIssueFormatter` ships a sensible Markdown format, but you can inject your own implementation via the two-argument constructor available on every posting service:

```java
IssueFormatter customFormatter = new MyCompanyFormatter();

new GitHubPostingService(gitHubConfig, customFormatter);
new JiraPostingService(jiraConfig, customFormatter);
new GitLabPostingService(gitLabConfig, customFormatter);
new EmailPostingService(emailConfig, customFormatter);
```

The single-argument constructors (config only) default to `DefaultIssueFormatter`. See [Custom Posting Service](custom-posting-service.md) for details on implementing `PostingService` from scratch.

---

## Why not official SDKs?

Every posting module uses the JDK built-in `java.net.http.HttpClient` and Gson rather than a platform-specific SDK. This is a deliberate design choice:

- **No official Java SDKs exist.** GitHub does not publish a Java SDK (only JavaScript, Ruby, .NET). Atlassian does not ship a maintained Jira Cloud client library for Java. GitLab has no official Java SDK either. The only options are third-party community libraries.
- **Dependency weight.** The most popular community libraries pull in heavy framework stacks. `gitlab4j-api` depends on the entire Jersey JAX-RS framework. `github-api` (hub4j) brings in Apache Commons. The auto-generated Jira client (everit-org) drags in RxJava, Jackson, and Swagger annotations. For the three API calls Observarium needs per service, this is significant overkill.
- **Observarium is a library, not an application.** Libraries that pull in framework-level transitive dependencies create classpath conflicts and version-locking problems for their consumers. A Spring Boot user shouldn't be forced to reconcile a Jersey version brought in by the GitLab module.
- **We only need three operations.** Each posting module calls exactly three REST endpoints: search issues, create an issue, and post a comment. The implementation for each service is ~200 lines of transparent, testable code with no magic. There is no pagination, streaming, webhook handling, or other complexity that would justify an SDK.
- **Email already uses the standard library.** The Email module uses Jakarta Mail + Angus Mail, which is the Jakarta EE standard for SMTP — there is nothing lighter or more appropriate.

If your application already depends on one of these client libraries for other purposes, you can implement a custom `PostingService` that delegates to it — see [Custom Posting Service](custom-posting-service.md).

---

## Feature Matrix

| Service | Create issue | Deduplication | Comment on duplicate |
|---|---|---|---|
| GitHub | Yes | Yes | Yes |
| Jira | Yes | Yes | Yes |
| GitLab | Yes | Yes | Yes |
| Email | Yes | No | No |

---

## GitHub Issues

### How it works

1. `findDuplicate` searches open issues in the repository using the GitHub Issues API label filter. Each issue created by Observarium carries a label of the form `observarium-{first12charsOfFingerprint}`, and `findDuplicate` queries for open issues with that label.
2. If a match is found, `commentOnIssue` adds a Markdown comment to the existing issue with the new timestamp, thread name, trace ID (if present), and tags.
3. If no match is found, `createIssue` opens a new issue using the injected `IssueFormatter` to produce the title and Markdown body. The issue is tagged with both the `labelPrefix` label (default `observarium`) and the fingerprint-specific label.

### Configuration

**Builder**

```java
import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.github.GitHubConfig;
import io.hephaistos.observarium.github.GitHubPostingService;

Observarium obs = Observarium.builder()
    .addPostingService(new GitHubPostingService(
        GitHubConfig.of("ghp_yourtoken", "owner", "repo")))
    .build();
```

To use a custom label prefix, use the full 4-argument constructor instead:

```java
new GitHubPostingService(new GitHubConfig("ghp_yourtoken", "owner", "repo", "my-app"))
```

**Spring Boot (`application.yml`)**

```yaml
observarium:
  github:
    owner: owner
    repo: repo
    token: ${GITHUB_TOKEN}
```

**Quarkus (`application.properties`)**

```properties
observarium.github.owner=owner
observarium.github.repo=repo
observarium.github.token=${GITHUB_TOKEN}
```

### Required token permissions

For GitHub Personal Access Tokens (classic):

| Scope | Reason |
|---|---|
| `repo` (or `public_repo` for public repos) | Read and write issues |

For fine-grained tokens:

| Permission | Level | Reason |
|---|---|---|
| Issues | Read and write | Create issues, add comments, search issue bodies |

### Deduplication mechanism

Deduplication is label-based. Every issue created by Observarium carries a label of the form `observarium-{first12charsOfFingerprint}`, for example `observarium-a3f8c2d1e4b5`. `findDuplicate` queries the GitHub Issues API for open issues in the repository filtered by that label. Only the first 12 characters of the SHA-256 fingerprint are used to stay within GitHub's label length limits while still providing sufficient uniqueness in practice.

Closed issues are excluded from the search; if the matched issue was closed, a new issue is created.

---

## Jira

### How it works

1. `findDuplicate` runs a JQL query against the configured project searching for a label that encodes the fingerprint, e.g. `observarium-a3f8c2d1e4b5` (first 12 characters of the SHA-256 fingerprint).
2. If a matching open issue is found, `commentOnIssue` adds a comment using Jira's comment API with Markdown-formatted content.
3. If no match is found, `createIssue` opens a new issue. Both the `observarium` label and the fingerprint-specific label are set on creation so future duplicates are found by `findDuplicate`.

### Configuration

**Builder**

```java
import io.hephaistos.observarium.jira.JiraConfig;
import io.hephaistos.observarium.jira.JiraPostingService;

Observarium obs = Observarium.builder()
    .addPostingService(new JiraPostingService(
        JiraConfig.of(
            "https://myorg.atlassian.net", // Jira base URL
            "alice@example.com",            // username (account email for Jira Cloud)
            "your-api-token",               // Jira API token
            "OPS"                           // project key
        )))
    .build();
```

To set a custom issue type, use the full 5-argument constructor instead:

```java
new JiraPostingService(new JiraConfig(
    "https://myorg.atlassian.net", "alice@example.com", "your-api-token", "OPS", "Task"))
```

**Spring Boot (`application.yml`)**

```yaml
observarium:
  jira:
    base-url: https://myorg.atlassian.net
    username: ${JIRA_USERNAME}
    api-token: ${JIRA_TOKEN}
    project-key: OPS
```

**Quarkus (`application.properties`)**

```properties
observarium.jira.base-url=https://myorg.atlassian.net
observarium.jira.username=${JIRA_USERNAME}
observarium.jira.api-token=${JIRA_TOKEN}
observarium.jira.project-key=OPS
```

### Required permissions

The Jira account used must have the following project permissions:

| Permission | Reason |
|---|---|
| Browse Projects | Find existing issues via JQL |
| Create Issues | Open new bug reports |
| Add Comments | Post recurrence comments |
| Edit Issues | Set labels on new issues |

Jira Cloud uses HTTP Basic Auth with the account email and an API token generated at [id.atlassian.com](https://id.atlassian.com/manage-profile/security/api-tokens). Jira Data Center uses the same Basic Auth mechanism with a Personal Access Token instead of the API token.

### JQL deduplication query

The implementation searches with a query of the form:

```
project = "OPS" AND labels = "observarium-<first12charsOfFingerprint>" AND statusCategory != Done
```

Only issues in non-terminal states are considered duplicates. If the issue was resolved, a new issue is created.

### Issue format

Issue bodies use Jira's Atlassian Document Format (ADF). Every created issue carries the labels `observarium` and `observarium-<first12charsOfFingerprint>` to support future deduplication queries.

---

## GitLab Issues

### How it works

1. `findDuplicate` searches for an open issue in the project that carries the label `observarium-{first12charsOfFingerprint}` using the GitLab Issues API label filter.
2. If a matching issue is found, `commentOnIssue` posts a note to the issue.
3. If no match is found, `createIssue` creates a new issue with both the `observarium` umbrella label and the fingerprint-specific label.

### Configuration

**Builder**

```java
import io.hephaistos.observarium.gitlab.GitLabConfig;
import io.hephaistos.observarium.gitlab.GitLabPostingService;

Observarium obs = Observarium.builder()
    .addPostingService(new GitLabPostingService(new GitLabConfig(
        "https://gitlab.com",  // GitLab base URL; use your self-managed URL if needed
        "glpat-yourtoken",     // personal access token or project access token
        "12345678"             // numeric project ID (or namespace/project path)
    )))
    .build();
```

**Spring Boot (`application.yml`)**

```yaml
observarium:
  gitlab:
    base-url: https://gitlab.com
    private-token: ${GITLAB_TOKEN}
    project-id: "12345678"
```

**Quarkus (`application.properties`)**

```properties
observarium.gitlab.base-url=https://gitlab.com
observarium.gitlab.private-token=${GITLAB_TOKEN}
observarium.gitlab.project-id=12345678
```

### Required token scopes

| Scope | Reason |
|---|---|
| `api` | Full API access, required to create issues, add notes, and search by label |

A narrower alternative is `read_api` + `write_repository` but GitLab does not expose a write-issues scope separately. For project-scoped tokens, the token must have at least the `Developer` role on the project.

### Label-based deduplication

Every created issue receives two GitLab labels: the `observarium` umbrella label and a fingerprint-specific label of the form `observarium-{first12charsOfFingerprint}`, for example `observarium-a3f8c2d1e4b5`. `findDuplicate` uses the GitLab Issues API `labels` filter parameter to find open issues carrying the fingerprint-specific label.

---

## Email (SMTP)

### How it works

`createIssue` sends one email per captured exception. The subject line is produced by the injected `IssueFormatter` and the body is a plain-text rendering of the event. There is no deduplication: every event produces a new email. `findDuplicate` always returns `DuplicateSearchResult.notFound()`.

### Configuration

**Builder**

```java
import io.hephaistos.observarium.email.EmailConfig;
import io.hephaistos.observarium.email.EmailPostingService;

// 5-argument convenience constructor (port 587, STARTTLS enabled):
Observarium obs = Observarium.builder()
    .addPostingService(new EmailPostingService(new EmailConfig(
        "smtp.example.com",         // SMTP host
        "alerts@example.com",       // From address
        "oncall@example.com",       // To address
        "alerts@example.com",       // SMTP username
        "smtp-password"             // SMTP password
    )))
    .build();

// 7-argument constructor to override port or STARTTLS:
new EmailPostingService(new EmailConfig(
    "smtp.example.com",   // SMTP host
    465,                  // SMTP port
    "alerts@example.com", // From address
    "oncall@example.com", // To address
    "alerts@example.com", // SMTP username
    "smtp-password",      // SMTP password
    false                 // startTls
))
```

**Spring Boot (`application.yml`)**

```yaml
observarium:
  email:
    smtp-host: smtp.example.com
    smtp-port: 587
    username: ${SMTP_USERNAME}
    password: ${SMTP_PASSWORD}
    from: alerts@example.com
    to: oncall@example.com
```

**Quarkus (`application.properties`)**

```properties
observarium.email.smtp-host=smtp.example.com
observarium.email.smtp-port=587
observarium.email.username=${SMTP_USERNAME}
observarium.email.password=${SMTP_PASSWORD}
observarium.email.from=alerts@example.com
observarium.email.to=oncall@example.com
```

### SMTP setup notes

- The implementation uses STARTTLS on port 587 by default. If your server requires implicit TLS (port 465), configure accordingly.
- `PostingResult.externalIssueId()` and `PostingResult.url()` are not meaningful for Email; the result carries only the success flag and an error message on failure.
- Because Email has no dedup capability, using it alongside a dedup-capable service (e.g. GitHub) is a common pattern: GitHub deduplicates while Email provides an immediate notification channel.
