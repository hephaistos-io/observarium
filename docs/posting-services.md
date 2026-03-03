# Posting Services

Observarium dispatches each captured exception to every configured `PostingService`. The processor first calls `findDuplicate()` on the service; if a duplicate is found it calls `commentOnIssue()`, otherwise it calls `createIssue()`. Every call returns a `PostingResult` that carries a success flag, the external issue ID, and a URL.

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

1. `findDuplicate` searches open issues in the repository for an HTML comment of the form `<!-- observarium:fingerprint:<sha256> -->`. This marker is embedded by `IssueFormatter.fingerprintMarker()` in every issue body that Observarium creates.
2. If a match is found, `commentOnIssue` adds a Markdown comment to the existing issue with the new timestamp, thread name, trace ID (if present), and tags.
3. If no match is found, `createIssue` opens a new issue using `IssueFormatter.title()` and `IssueFormatter.markdownBody()`.

### Configuration

**Builder**

```java
import io.hephaistos.observarium.Observarium;
// from observarium-github module:
// import io.hephaistos.observarium.github.GitHubPostingService;

Observarium obs = Observarium.builder()
    .addPostingService(new GitHubPostingService(
        "owner/repo",   // repository in owner/repo format
        "ghp_yourtoken" // personal access token or fine-grained token
    ))
    .build();
```

**Spring Boot (`application.yml`)**

```yaml
observarium:
  github:
    repository: owner/repo
    token: ${GITHUB_TOKEN}
```

**Quarkus (`application.properties`)**

```properties
observarium.github.repository=owner/repo
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

The fingerprint is written as an invisible HTML comment in the issue body:

```
<!-- observarium:fingerprint:a3f8c2... -->
```

`findDuplicate` performs a GitHub Issues search scoped to the repository. The search uses the fingerprint value so only the exact structural match triggers a duplicate. Closed issues are excluded; reopening a closed issue is outside the current implementation scope.

---

## Jira

### How it works

1. `findDuplicate` runs a JQL query against the configured project searching for a label that encodes the fingerprint, e.g. `observarium-fingerprint-a3f8c2`.
2. If a matching open issue is found, `commentOnIssue` adds a comment using Jira's comment API with Markdown-formatted content.
3. If no match is found, `createIssue` opens a new issue. The fingerprint label is set on creation so future duplicates are found by `findDuplicate`.

### Configuration

**Builder**

```java
Observarium obs = Observarium.builder()
    .addPostingService(new JiraPostingService(
        "https://myorg.atlassian.net", // Jira base URL
        "OPS",                          // project key
        "alice@example.com",            // account email
        "your-api-token"                // Jira API token
    ))
    .build();
```

**Spring Boot (`application.yml`)**

```yaml
observarium:
  jira:
    base-url: https://myorg.atlassian.net
    project-key: OPS
    email: ${JIRA_EMAIL}
    api-token: ${JIRA_TOKEN}
```

**Quarkus (`application.properties`)**

```properties
observarium.jira.base-url=https://myorg.atlassian.net
observarium.jira.project-key=OPS
observarium.jira.email=${JIRA_EMAIL}
observarium.jira.api-token=${JIRA_TOKEN}
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
project = OPS AND labels = "observarium-fingerprint-<sha256>" AND statusCategory != Done
```

Only issues in non-terminal states are considered duplicates. If the issue was resolved, a new issue is created.

### Issue format

Issue bodies use Jira's Atlassian Document Format (ADF). The label `observarium-fingerprint-<sha256>` is applied to every created issue to support future deduplication queries.

---

## GitLab Issues

### How it works

1. `findDuplicate` searches for an open issue in the project that carries the label `observarium::fingerprint::<sha256>`.
2. If a matching issue is found, `commentOnIssue` posts a note to the issue.
3. If no match is found, `createIssue` creates a new issue with the fingerprint label.

### Configuration

**Builder**

```java
Observarium obs = Observarium.builder()
    .addPostingService(new GitLabPostingService(
        "https://gitlab.com",  // GitLab base URL; use your self-managed URL if needed
        "12345678",            // numeric project ID (or namespace/project path)
        "glpat-yourtoken"      // personal access token or project access token
    ))
    .build();
```

**Spring Boot (`application.yml`)**

```yaml
observarium:
  gitlab:
    base-url: https://gitlab.com
    project-id: "12345678"
    token: ${GITLAB_TOKEN}
```

**Quarkus (`application.properties`)**

```properties
observarium.gitlab.base-url=https://gitlab.com
observarium.gitlab.project-id=12345678
observarium.gitlab.token=${GITLAB_TOKEN}
```

### Required token scopes

| Scope | Reason |
|---|---|
| `api` | Full API access, required to create issues, add notes, and search by label |

A narrower alternative is `read_api` + `write_repository` but GitLab does not expose a write-issues scope separately. For project-scoped tokens, the token must have at least the `Developer` role on the project.

### Label-based deduplication

Every created issue receives the GitLab label `observarium::fingerprint::<sha256>`. `findDuplicate` uses the GitLab Issues API `labels` filter parameter to find open issues carrying that label. The `observarium::` prefix makes these labels a GitLab scoped label group, keeping the Observarium labels visually grouped in the GitLab UI.

---

## Email (SMTP)

### How it works

`createIssue` sends one email per captured exception. The subject line follows `IssueFormatter.title()` format and the body is the Markdown issue body. There is no deduplication: every event produces a new email. `findDuplicate` always returns `DuplicateSearchResult.notFound()`.

### Configuration

**Builder**

```java
Observarium obs = Observarium.builder()
    .addPostingService(new EmailPostingService(
        "smtp.example.com",         // SMTP host
        587,                        // SMTP port
        "alerts@example.com",       // SMTP username
        "smtp-password",            // SMTP password
        "alerts@example.com",       // From address
        "oncall@example.com"        // To address
    ))
    .build();
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
