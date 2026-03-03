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

/**
 * CDI producer that creates the central {@link Observarium} bean for Quarkus applications.
 *
 * <p>Consumes the {@link ObservariumQuarkusConfig} and all {@link PostingService} beans
 * registered in the CDI container (including those produced by the posting service producers
 * in this class) to build a fully configured {@link Observarium} instance.
 */
@ApplicationScoped
public class ObservariumProducer {

    @Inject
    ObservariumQuarkusConfig config;

    /**
     * Produces the singleton {@link Observarium} bean, injecting all available
     * {@link PostingService} instances discovered from the CDI context.
     */
    @Produces
    @ApplicationScoped
    public Observarium observarium(Instance<PostingService> postingServices) {
        ScrubLevel scrubLevel = ScrubLevel.valueOf(config.scrubLevel());

        var builder = Observarium.builder()
                .scrubLevel(scrubLevel)
                .fingerprinter(new DefaultExceptionFingerprinter())
                .scrubber(new DefaultDataScrubber(scrubLevel))
                .traceContextProvider(new MdcTraceContextProvider(
                        config.traceIdMdcKey(),
                        config.spanIdMdcKey()
                ));

        for (PostingService service : postingServices) {
            builder.addPostingService(service);
        }

        return builder.build();
    }

    // -------------------------------------------------------------------------
    // Posting service producers
    // -------------------------------------------------------------------------

    /**
     * Produces a GitHub {@link PostingService} when {@code observarium.github.enabled=true}
     * and the required configuration properties are present.
     *
     * <p>This producer method references {@code GitHubPostingService} directly. The class is
     * available at compile time via the {@code compileOnly} dependency on
     * {@code observarium-github}. At runtime, if the module is absent, this producer method
     * will not be invoked because the enabled check guards instantiation.
     */
    @Produces
    @ApplicationScoped
    public io.hephaistos.observarium.github.GitHubPostingService gitHubPostingService() {
        return config.github()
                .filter(ObservariumQuarkusConfig.GitHub::enabled)
                .map(gh -> new io.hephaistos.observarium.github.GitHubPostingService(
                        new io.hephaistos.observarium.github.GitHubConfig(
                                gh.token().orElseThrow(() -> new IllegalStateException(
                                        "observarium.github.token is required when github is enabled")),
                                gh.owner().orElseThrow(() -> new IllegalStateException(
                                        "observarium.github.owner is required when github is enabled")),
                                gh.repo().orElseThrow(() -> new IllegalStateException(
                                        "observarium.github.repo is required when github is enabled")),
                                gh.labelPrefix()
                        )
                ))
                .orElse(null);
    }

    /**
     * Produces a Jira {@link PostingService} when {@code observarium.jira.enabled=true}.
     */
    @Produces
    @ApplicationScoped
    public io.hephaistos.observarium.jira.JiraPostingService jiraPostingService() {
        return config.jira()
                .filter(ObservariumQuarkusConfig.Jira::enabled)
                .map(jira -> new io.hephaistos.observarium.jira.JiraPostingService(
                        new io.hephaistos.observarium.jira.JiraConfig(
                                jira.baseUrl().orElseThrow(() -> new IllegalStateException(
                                        "observarium.jira.base-url is required when jira is enabled")),
                                jira.username().orElseThrow(() -> new IllegalStateException(
                                        "observarium.jira.username is required when jira is enabled")),
                                jira.apiToken().orElseThrow(() -> new IllegalStateException(
                                        "observarium.jira.api-token is required when jira is enabled")),
                                jira.projectKey().orElseThrow(() -> new IllegalStateException(
                                        "observarium.jira.project-key is required when jira is enabled")),
                                jira.issueType()
                        )
                ))
                .orElse(null);
    }

    /**
     * Produces a GitLab {@link PostingService} when {@code observarium.gitlab.enabled=true}.
     */
    @Produces
    @ApplicationScoped
    public io.hephaistos.observarium.gitlab.GitLabPostingService gitLabPostingService() {
        return config.gitlab()
                .filter(ObservariumQuarkusConfig.GitLab::enabled)
                .map(gl -> new io.hephaistos.observarium.gitlab.GitLabPostingService(
                        new io.hephaistos.observarium.gitlab.GitLabConfig(
                                gl.baseUrl().orElseThrow(() -> new IllegalStateException(
                                        "observarium.gitlab.base-url is required when gitlab is enabled")),
                                gl.privateToken().orElseThrow(() -> new IllegalStateException(
                                        "observarium.gitlab.private-token is required when gitlab is enabled")),
                                gl.projectId().orElseThrow(() -> new IllegalStateException(
                                        "observarium.gitlab.project-id is required when gitlab is enabled"))
                        )
                ))
                .orElse(null);
    }

    /**
     * Produces an Email {@link PostingService} when {@code observarium.email.enabled=true}.
     */
    @Produces
    @ApplicationScoped
    public io.hephaistos.observarium.email.EmailPostingService emailPostingService() {
        return config.email()
                .filter(ObservariumQuarkusConfig.Email::enabled)
                .map(em -> new io.hephaistos.observarium.email.EmailPostingService(
                        new io.hephaistos.observarium.email.EmailConfig(
                                em.smtpHost().orElseThrow(() -> new IllegalStateException(
                                        "observarium.email.smtp-host is required when email is enabled")),
                                em.smtpPort(),
                                em.from().orElseThrow(() -> new IllegalStateException(
                                        "observarium.email.from is required when email is enabled")),
                                em.to().orElseThrow(() -> new IllegalStateException(
                                        "observarium.email.to is required when email is enabled")),
                                em.username().orElse(null),
                                em.password().orElse(null),
                                em.startTls()
                        )
                ))
                .orElse(null);
    }
}
