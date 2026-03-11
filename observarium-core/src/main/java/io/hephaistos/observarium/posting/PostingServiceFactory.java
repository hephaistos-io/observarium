package io.hephaistos.observarium.posting;

import java.util.Map;
import java.util.Optional;

/**
 * SPI for constructing {@link PostingService} instances from flat configuration maps.
 *
 * <p>Each posting module (GitHub, Jira, GitLab, Email) provides an implementation registered via
 * {@code META-INF/services/io.hephaistos.observarium.posting.PostingServiceFactory}. Framework
 * modules (Spring Boot, Quarkus) discover factories at runtime via {@link java.util.ServiceLoader},
 * extract config as {@code Map<String, String>} from their config system, and delegate
 * construction.
 *
 * <p>The contract:
 *
 * <ul>
 *   <li>{@link #create} returns {@link Optional#empty()} when the {@code enabled} key is absent or
 *       {@code "false"}.
 *   <li>{@link #create} throws {@link IllegalArgumentException} when enabled but required
 *       configuration is missing.
 * </ul>
 */
public interface PostingServiceFactory {

  /**
   * Returns the identifier for this factory, matching the config prefix segment.
   *
   * <p>For example, {@code "github"} matches config keys under {@code observarium.github.*}.
   *
   * @return non-null, non-empty identifier (e.g. {@code "github"}, {@code "jira"}, {@code
   *     "gitlab"}, {@code "email"})
   */
  String id();

  /**
   * Creates a {@link PostingService} from the given configuration map.
   *
   * <p>Keys in the map are kebab-case and prefix-stripped (e.g. for the GitHub factory with prefix
   * {@code observarium.github}, the key {@code observarium.github.token} appears as {@code
   * "token"}).
   *
   * @param config prefix-stripped configuration entries; never null
   * @return a configured posting service, or empty if the service is not enabled
   * @throws IllegalArgumentException if enabled but required configuration is missing
   */
  Optional<PostingService> create(Map<String, String> config);
}
