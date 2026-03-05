package io.hephaistos.observarium.quarkus;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Optional;

/**
 * Quarkus-style configuration mapping for Observarium, bound from the {@code observarium.*} config
 * namespace via SmallRye Config.
 *
 * <p>All methods return configuration values directly; nested sections are modeled as inner
 * interfaces. Optional sub-configurations (e.g. posting service credentials) use {@link Optional}
 * to allow the config sub-tree to be absent.
 */
@ConfigMapping(prefix = "observarium")
public interface ObservariumQuarkusConfig {

  @WithDefault("true")
  boolean enabled();

  @WithDefault("BASIC")
  String scrubLevel();

  @WithDefault("trace_id")
  String traceIdMdcKey();

  @WithDefault("span_id")
  String spanIdMdcKey();

  Optional<GitHub> github();

  Optional<Jira> jira();

  Optional<GitLab> gitlab();

  Optional<Email> email();

  interface GitHub {
    @WithDefault("false")
    boolean enabled();

    Optional<String> token();

    Optional<String> owner();

    Optional<String> repo();

    @WithDefault("observarium")
    String labelPrefix();
  }

  interface Jira {
    @WithDefault("false")
    boolean enabled();

    Optional<String> baseUrl();

    Optional<String> username();

    Optional<String> apiToken();

    Optional<String> projectKey();

    @WithDefault("Bug")
    String issueType();
  }

  interface GitLab {
    @WithDefault("false")
    boolean enabled();

    Optional<String> baseUrl();

    Optional<String> privateToken();

    Optional<String> projectId();
  }

  interface Email {
    @WithDefault("false")
    boolean enabled();

    Optional<String> smtpHost();

    @WithDefault("587")
    int smtpPort();

    Optional<String> from();

    Optional<String> to();

    Optional<String> username();

    Optional<String> password();

    @WithDefault("true")
    boolean startTls();
  }
}
