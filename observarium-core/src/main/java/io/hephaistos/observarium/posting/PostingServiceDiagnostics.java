package io.hephaistos.observarium.posting;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;

/** Shared startup diagnostic for {@link PostingServiceFactory} implementations. */
public final class PostingServiceDiagnostics {

  private PostingServiceDiagnostics() {}

  /**
   * Logs a WARN if {@code config} carries every key in {@code requiredKeys} (present and
   * non-blank), flagging a service that was configured but left disabled. Call only from the
   * disabled branch of {@link PostingServiceFactory#create}.
   *
   * @param log the calling factory's logger; the warning is attributed to that factory
   * @param serviceId the config prefix segment, matching {@link PostingServiceFactory#id()}
   * @param config the prefix-stripped configuration map passed to {@code create}
   * @param requiredKeys the keys that together indicate the service was meant to be used; callers
   *     with conditional requirements (e.g. Email's credentials, required only under {@code auth})
   *     resolve those first and pass the effective list
   */
  public static void warnIfConfiguredButNotEnabled(
      Logger log, String serviceId, Map<String, String> config, List<String> requiredKeys) {
    if (requiredKeys.isEmpty() || !requiredKeys.stream().allMatch(key -> isPresent(config, key))) {
      return;
    }
    log.warn(
        "Observarium: {} is configured ({} present) but not enabled — set {}.enabled=true to"
            + " activate it",
        serviceId,
        String.join(", ", requiredKeys),
        serviceId);
  }

  private static boolean isPresent(Map<String, String> config, String key) {
    String value = config.get(key);
    return value != null && !value.isBlank();
  }
}
