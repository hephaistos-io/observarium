package io.hephaistos.observarium.posting;

import java.util.Map;

/**
 * Strict readers for the flat configuration maps passed to {@link PostingServiceFactory#create}.
 */
public final class PostingConfig {

  private PostingConfig() {}

  /**
   * Reads a boolean flag, accepting only {@code "true"} and {@code "false"} (case-insensitive,
   * surrounding whitespace ignored).
   *
   * @param config prefix-stripped configuration entries
   * @param key the key to read
   * @param defaultValue value used when the key is absent or blank
   * @return the parsed flag
   * @throws IllegalArgumentException if the value is neither {@code "true"} nor {@code "false"}
   */
  public static boolean booleanValue(Map<String, String> config, String key, boolean defaultValue) {
    String value = config.get(key);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    String trimmed = value.trim();
    if ("true".equalsIgnoreCase(trimmed)) {
      return true;
    }
    if ("false".equalsIgnoreCase(trimmed)) {
      return false;
    }
    throw new IllegalArgumentException(
        "Configuration key '" + key + "' must be 'true' or 'false', got: " + value);
  }
}
