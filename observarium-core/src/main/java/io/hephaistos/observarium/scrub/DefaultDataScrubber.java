package io.hephaistos.observarium.scrub;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Default {@link DataScrubber} that replaces sensitive substrings with {@code [REDACTED]} using a
 * set of built-in regex patterns gated by {@link ScrubLevel}.
 *
 * <p>The built-in patterns are organised by severity:
 *
 * <ul>
 *   <li>{@link ScrubLevel#BASIC} — credential-style key-value pairs (e.g. {@code password=secret},
 *       {@code token: abc123}) and Bearer tokens.
 *   <li>{@link ScrubLevel#STRICT} — everything in BASIC, plus email addresses, IPv4 and IPv6
 *       addresses, and US-format phone numbers.
 * </ul>
 *
 * <p>At {@link ScrubLevel#NONE} no substitution is performed at all — the input is returned as-is,
 * including bypassing any custom additional patterns.
 */
public class DefaultDataScrubber implements DataScrubber {

  private static final String REDACTED = "[REDACTED]";

  // Matches full 8-group IPv6 (e.g. 2001:0db8:85a3:0000:0000:8a2e:0370:7334) and
  // compressed forms that contain "::" (e.g. ::1, fe80::1, 2001:db8::1).
  // Known trade-off: any <hex>::<hex> token (e.g. abc::def) is a valid compressed
  // IPv6 address and will be matched, even if it was intended as a scope-resolution
  // operator (C++, Rust). This is acceptable for a STRICT-level scrubber where
  // over-redaction is preferred over leaking addresses.
  private static final Pattern IPV6_PATTERN =
      Pattern.compile(
          // Full form: exactly 8 colon-separated hex groups (no ::)
          "(?<![.\\w])(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}(?![.:\\w])"
              // Compressed with prefix: groups::tail — e.g. fe80::1, 2001:db8::1
              + "|(?<![.\\w])[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{1,4})*::(?:[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{1,4})*)?(?![.:\\w])"
              // Compressed at start: ::tail — e.g. ::1, ::ffff:10.0.0.1
              + "|(?<![.:\\w])::(?:[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{1,4})*)?(?![.:\\w])");

  private static final List<PatternLevel> PATTERNS =
      List.of(
          new PatternLevel(
              ScrubLevel.BASIC,
              Pattern.compile(
                  "(?i)(password|passwd|pwd|secret|token|api_key|apikey|"
                      + "authorization|auth_token|access_token|private_key)\\s*[:=]\\s*\\S{1,200}")),
          new PatternLevel(
              ScrubLevel.BASIC, Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9\\-._~+/]+=*")),
          new PatternLevel(
              ScrubLevel.STRICT,
              Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}")),
          new PatternLevel(
              ScrubLevel.STRICT,
              Pattern.compile("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b")),
          new PatternLevel(ScrubLevel.STRICT, IPV6_PATTERN),
          new PatternLevel(
              ScrubLevel.STRICT, Pattern.compile("(?i)\\b\\d{3}[\\-.]?\\d{3}[\\-.]?\\d{4}\\b")));

  private final ScrubLevel level;
  private final List<Pattern> additionalPatterns;

  public DefaultDataScrubber(ScrubLevel level) {
    this(level, List.of());
  }

  /**
   * Creates a scrubber that applies the built-in patterns for the given level and also applies
   * every pattern in {@code additionalPatterns} at levels other than {@code NONE}.
   *
   * <p>Custom patterns are evaluated after the built-in ones at levels other than {@code NONE}. At
   * {@link ScrubLevel#NONE} the input is returned unchanged and no patterns (built-in or custom)
   * are applied.
   *
   * @param level the scrubbing aggressiveness for built-in patterns; never null
   * @param additionalPatterns extra patterns to always apply; never null, may be empty
   */
  public DefaultDataScrubber(ScrubLevel level, List<Pattern> additionalPatterns) {
    this.level = level;
    this.additionalPatterns = List.copyOf(additionalPatterns);
  }

  /**
   * Returns the input unchanged when the configured level is {@link ScrubLevel#NONE} and no
   * additional patterns were supplied. All replacements use the literal string {@code [REDACTED]}.
   */
  @Override
  public String scrub(String text) {
    if (text == null || level == ScrubLevel.NONE) {
      return text;
    }
    String result = text;
    for (var patternLevel : PATTERNS) {
      if (patternLevel.level.ordinal() <= level.ordinal()) {
        result = patternLevel.pattern.matcher(result).replaceAll(REDACTED);
      }
    }
    for (var pattern : additionalPatterns) {
      result = pattern.matcher(result).replaceAll(REDACTED);
    }
    return result;
  }

  private record PatternLevel(ScrubLevel level, Pattern pattern) {}
}
