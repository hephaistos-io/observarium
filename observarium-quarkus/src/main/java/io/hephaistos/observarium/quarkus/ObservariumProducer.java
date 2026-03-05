package io.hephaistos.observarium.quarkus;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.fingerprint.DefaultExceptionFingerprinter;
import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.scrub.DefaultDataScrubber;
import io.hephaistos.observarium.scrub.ScrubLevel;
import io.hephaistos.observarium.trace.MdcTraceContextProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CDI producer that creates the central {@link Observarium} bean for Quarkus applications.
 *
 * <p>Posting service beans are created conditionally based on config and classpath availability
 * using reflection, avoiding {@code NoClassDefFoundError} when optional posting modules are absent.
 */
@ApplicationScoped
public class ObservariumProducer {

  private static final Logger log = LoggerFactory.getLogger(ObservariumProducer.class);

  @Inject ObservariumQuarkusConfig config;

  @Produces
  @ApplicationScoped
  public Observarium observarium(Instance<PostingService> discoveredServices) {
    if (!config.enabled()) {
      log.info("Observarium is disabled via configuration");
      return Observarium.builder().build();
    }

    ScrubLevel scrubLevel = ScrubLevel.valueOf(config.scrubLevel());

    var builder =
        Observarium.builder()
            .scrubLevel(scrubLevel)
            .fingerprinter(new DefaultExceptionFingerprinter())
            .scrubber(new DefaultDataScrubber(scrubLevel))
            .traceContextProvider(
                new MdcTraceContextProvider(config.traceIdMdcKey(), config.spanIdMdcKey()));

    // Add any PostingService beans discovered via CDI
    for (PostingService service : discoveredServices) {
      builder.addPostingService(service);
    }

    // Additionally, create posting services from config if the classes are on the classpath
    for (PostingService service : createConfiguredPostingServices()) {
      builder.addPostingService(service);
    }

    return builder.build();
  }

  private List<PostingService> createConfiguredPostingServices() {
    List<PostingService> services = new ArrayList<>();

    config
        .github()
        .filter(ObservariumQuarkusConfig.GitHub::enabled)
        .ifPresent(
            gh -> {
              try {
                var ghConfig =
                    new io.hephaistos.observarium.github.GitHubConfig(
                        gh.token()
                            .orElseThrow(
                                () ->
                                    new IllegalStateException(
                                        "observarium.github.token is required when github is enabled")),
                        gh.owner()
                            .orElseThrow(
                                () ->
                                    new IllegalStateException(
                                        "observarium.github.owner is required when github is enabled")),
                        gh.repo()
                            .orElseThrow(
                                () ->
                                    new IllegalStateException(
                                        "observarium.github.repo is required when github is enabled")),
                        gh.labelPrefix());
                services.add(new io.hephaistos.observarium.github.GitHubPostingService(ghConfig));
              } catch (NoClassDefFoundError e) {
                log.warn(
                    "GitHub posting service is enabled but observarium-github is not on the classpath");
              }
            });

    config
        .jira()
        .filter(ObservariumQuarkusConfig.Jira::enabled)
        .ifPresent(
            jira -> {
              try {
                var jiraConfig =
                    new io.hephaistos.observarium.jira.JiraConfig(
                        jira.baseUrl()
                            .orElseThrow(
                                () ->
                                    new IllegalStateException(
                                        "observarium.jira.base-url is required when jira is enabled")),
                        jira.username()
                            .orElseThrow(
                                () ->
                                    new IllegalStateException(
                                        "observarium.jira.username is required when jira is enabled")),
                        jira.apiToken()
                            .orElseThrow(
                                () ->
                                    new IllegalStateException(
                                        "observarium.jira.api-token is required when jira is enabled")),
                        jira.projectKey()
                            .orElseThrow(
                                () ->
                                    new IllegalStateException(
                                        "observarium.jira.project-key is required when jira is enabled")),
                        jira.issueType());
                services.add(new io.hephaistos.observarium.jira.JiraPostingService(jiraConfig));
              } catch (NoClassDefFoundError e) {
                log.warn(
                    "Jira posting service is enabled but observarium-jira is not on the classpath");
              }
            });

    config
        .gitlab()
        .filter(ObservariumQuarkusConfig.GitLab::enabled)
        .ifPresent(
            gl -> {
              try {
                var glConfig =
                    new io.hephaistos.observarium.gitlab.GitLabConfig(
                        gl.baseUrl()
                            .orElseThrow(
                                () ->
                                    new IllegalStateException(
                                        "observarium.gitlab.base-url is required when gitlab is enabled")),
                        gl.privateToken()
                            .orElseThrow(
                                () ->
                                    new IllegalStateException(
                                        "observarium.gitlab.private-token is required when gitlab is enabled")),
                        gl.projectId()
                            .orElseThrow(
                                () ->
                                    new IllegalStateException(
                                        "observarium.gitlab.project-id is required when gitlab is enabled")));
                services.add(new io.hephaistos.observarium.gitlab.GitLabPostingService(glConfig));
              } catch (NoClassDefFoundError e) {
                log.warn(
                    "GitLab posting service is enabled but observarium-gitlab is not on the classpath");
              }
            });

    config
        .email()
        .filter(ObservariumQuarkusConfig.Email::enabled)
        .ifPresent(
            em -> {
              try {
                var emailConfig =
                    new io.hephaistos.observarium.email.EmailConfig(
                        em.smtpHost()
                            .orElseThrow(
                                () ->
                                    new IllegalStateException(
                                        "observarium.email.smtp-host is required when email is enabled")),
                        em.smtpPort(),
                        em.from()
                            .orElseThrow(
                                () ->
                                    new IllegalStateException(
                                        "observarium.email.from is required when email is enabled")),
                        em.to()
                            .orElseThrow(
                                () ->
                                    new IllegalStateException(
                                        "observarium.email.to is required when email is enabled")),
                        em.username().orElse(null),
                        em.password().orElse(null),
                        em.startTls());
                services.add(new io.hephaistos.observarium.email.EmailPostingService(emailConfig));
              } catch (NoClassDefFoundError e) {
                log.warn(
                    "Email posting service is enabled but observarium-email is not on the classpath");
              }
            });

    return services;
  }
}
