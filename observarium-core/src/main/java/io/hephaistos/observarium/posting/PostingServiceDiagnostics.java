package io.hephaistos.observarium.posting;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;

/**
 * Shared startup diagnostic for {@link PostingServiceFactory} implementations.
 *
 * <p>Warns when a service's configuration is complete enough that it was clearly meant to be
 * activated, but the {@code enabled} flag was left unset (or set to something other than {@code
 * "true"}). This does not change {@link PostingServiceFactory#create} behavior in any way — {@code
 * create} still returns {@link java.util.Optional#empty()} whenever {@code enabled} is not {@code
 * "true"}, exactly as documented. It exists purely to turn "why is nothing being filed" into a
 * readable startup log line instead of a factory-source read.
 *
 * <p>Deliberately conservative: a service with no configuration, or only some of its required keys,
 * produces no warning — every application would otherwise see a warning for every posting service
 * it does not use, which is worse than the silence this is meant to fix. The warning fires only
 * when every required key is present and non-blank.
 */
public final class PostingServiceDiagnostics {

  private PostingServiceDiagnostics() {}

  /**
   * Logs a WARN if {@code config} carries every key in {@code requiredKeys} (present and non-blank)
   * but {@code enabled} is not {@code "true"}.
   *
   * @param log the calling factory's logger; the warning is attributed to that factory
   * @param serviceId the config prefix segment, matching {@link PostingServiceFactory#id()} (e.g.
   *     {@code "github"})
   * @param config the prefix-stripped configuration map passed to {@code create}
   * @param requiredKeys the keys that, together, indicate the caller clearly intended to configure
   *     this service; callers with conditional requirements (e.g. keys only required under another
   *     key's value) should resolve those first and pass the effective list
   */
  public static void warnIfConfiguredButNotEnabled(
      Logger log, String serviceId, Map<String, String> config, List<String> requiredKeys) {
    if (requiredKeys.isEmpty()) {
      return;
    }
    String enabled = config.getOrDefault("enabled", "false");
    if ("true".equalsIgnoreCase(enabled)) {
      return;
    }
    List<String> present = requiredKeys.stream().filter(key -> isPresent(config, key)).toList();
    if (present.size() != requiredKeys.size()) {
      return;
    }
    log.warn(
        "Observarium: {} is configured ({} present) but not enabled — set {}.enabled=true to"
            + " activate it",
        serviceId,
        String.join(", ", present),
        serviceId);
  }

  private static boolean isPresent(Map<String, String> config, String key) {
    String value = config.get(key);
    return value != null && !value.isBlank();
  }
}
