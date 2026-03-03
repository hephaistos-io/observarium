package io.hephaistos.observarium.scrub;

import java.util.List;
import java.util.regex.Pattern;

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

    public DefaultDataScrubber(ScrubLevel level) {
        this(level, List.of());
    }

    public DefaultDataScrubber(ScrubLevel level, List<Pattern> additionalPatterns) {
        this.level = level;
        this.additionalPatterns = List.copyOf(additionalPatterns);
    }

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
