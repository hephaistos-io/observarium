package io.hephaistos.observarium.scrub;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Default {@link DataScrubber} that replaces sensitive substrings with {@code [REDACTED]} using
 * a set of built-in regex patterns gated by {@link ScrubLevel}.
 *
 * <p>The built-in patterns are organised by severity:
 * <ul>
 *   <li>{@link ScrubLevel#BASIC} — credential-style key-value pairs
 *       (e.g. {@code password=secret}, {@code token: abc123}) and Bearer tokens.</li>
 *   <li>{@link ScrubLevel#STRICT} — everything in BASIC, plus email addresses, IPv4 addresses,
 *       and US-format phone numbers.</li>
 * </ul>
 *
 * <p>At {@link ScrubLevel#NONE} no substitution is performed and the input is returned as-is.
 *
 * <p>Additional custom patterns supplied at construction time are always applied regardless of
 * the configured level (even at {@code NONE}), making it easy to add application-specific rules
 * without replacing the built-in set.
 */
public class DefaultDataScrubber implements DataScrubber {

    private static final String REDACTED = "[REDACTED]";

    private static final List<PatternLevel> PATTERNS = List.of(
        new PatternLevel(ScrubLevel.BASIC,
            Pattern.compile("(?i)(password|passwd|pwd|secret|token|api_key|apikey|" +
                "authorization|auth_token|access_token|private_key)\\s*[:=]\\s*\\S{1,200}")),
        new PatternLevel(ScrubLevel.BASIC,
            Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9\\-._~+/]+=*")),
        new PatternLevel(ScrubLevel.STRICT,
            Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}")),
        new PatternLevel(ScrubLevel.STRICT,
            Pattern.compile("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b")),
        new PatternLevel(ScrubLevel.STRICT,
            Pattern.compile("(?i)\\b\\d{3}[\\-.]?\\d{3}[\\-.]?\\d{4}\\b"))
    );

    private final ScrubLevel level;
    private final List<Pattern> additionalPatterns;

    /**
     * Creates a scrubber that applies only the built-in patterns for the given level.
     *
     * @param level the scrubbing aggressiveness; never null
     */
    public DefaultDataScrubber(ScrubLevel level) {
        this(level, List.of());
    }

    /**
     * Creates a scrubber that applies the built-in patterns for the given level and also applies
     * every pattern in {@code additionalPatterns} unconditionally.
     *
     * <p>Custom patterns are evaluated after the built-in ones and are applied even when
     * {@code level} is {@link ScrubLevel#NONE}, so they act as mandatory redaction rules
     * layered on top of the standard set.
     *
     * @param level              the scrubbing aggressiveness for built-in patterns; never null
     * @param additionalPatterns extra patterns to always apply; never null, may be empty
     */
    public DefaultDataScrubber(ScrubLevel level, List<Pattern> additionalPatterns) {
        this.level = level;
        this.additionalPatterns = List.copyOf(additionalPatterns);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the input unchanged when it is {@code null} or when the configured level is
     * {@link ScrubLevel#NONE} and no additional patterns were supplied. All replacements use
     * the literal string {@code [REDACTED]}.
     *
     * @param text the raw string to scrub, may be null
     * @return the scrubbed string, or null if {@code text} was null
     */
    @Override
    public String scrub(String text) {
        if (text == null || level == ScrubLevel.NONE) {
            return text;
        }
        String result = text;
        for (PatternLevel pl : PATTERNS) {
            if (pl.level.ordinal() <= level.ordinal()) {
                result = pl.pattern.matcher(result).replaceAll(REDACTED);
            }
        }
        for (Pattern p : additionalPatterns) {
            result = p.matcher(result).replaceAll(REDACTED);
        }
        return result;
    }

    private record PatternLevel(ScrubLevel level, Pattern pattern) {}
}
