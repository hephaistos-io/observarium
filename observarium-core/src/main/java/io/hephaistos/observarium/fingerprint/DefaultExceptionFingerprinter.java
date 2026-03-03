package io.hephaistos.observarium.fingerprint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Set;

/**
 * Default {@link ExceptionFingerprinter} that derives a fingerprint from the exception's
 * structural identity rather than its runtime message or line numbers.
 *
 * <p>The fingerprint is built by concatenating, for each entry in the cause chain:
 * the fully-qualified exception class name followed by each stack frame's class and method name
 * (line numbers are excluded so that trivial source changes do not break deduplication). The
 * cause chain is walked up to ten levels deep with cycle detection. The resulting string is
 * then hashed with SHA-256 and returned as a lowercase hex string.
 *
 * <p>This strategy works well for most applications: two occurrences of the same exception
 * thrown from the same call path produce the same fingerprint, even across restarts. It will
 * produce distinct fingerprints for exceptions of the same type thrown from different call
 * sites, which is usually the desired behaviour.
 *
 * <p>Consider providing a custom {@link ExceptionFingerprinter} if:
 * <ul>
 *   <li>Your stack frames include generated or dynamic class names that change across
 *       deployments (e.g. lambda proxies, CGLIB subclasses).</li>
 *   <li>You want to bucket a broad family of exceptions under a single issue regardless of
 *       call site.</li>
 * </ul>
 */
public class DefaultExceptionFingerprinter implements ExceptionFingerprinter {

    private static final int MAX_CAUSE_DEPTH = 10;

    /**
     * {@inheritDoc}
     *
     * <p>The fingerprint is stable across restarts provided the exception type and call-site
     * structure do not change. It is not affected by the exception message, thread name,
     * timestamps, or line numbers.
     *
     * @param throwable the exception to fingerprint; never null
     * @return a 64-character lowercase hex SHA-256 digest; never null
     */
    @Override
    public String fingerprint(Throwable throwable) {
        var fingerprintInput = new StringBuilder();
        appendExceptionFrames(fingerprintInput, throwable);

        // Walk the cause chain with cycle detection and depth limit.
        var seen = Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        seen.add(throwable);
        var cause = throwable.getCause();
        int depth = 0;
        while (cause != null && depth < MAX_CAUSE_DEPTH && seen.add(cause)) {
            fingerprintInput.append("\ncaused_by:");
            appendExceptionFrames(fingerprintInput, cause);
            cause = cause.getCause();
            depth++;
        }
        return sha256(fingerprintInput.toString());
    }

    private static void appendExceptionFrames(StringBuilder sb, Throwable throwable) {
        sb.append(throwable.getClass().getName());
        for (StackTraceElement frame : throwable.getStackTrace()) {
            sb.append('\n');
            sb.append(frame.getClassName());
            sb.append('#');
            sb.append(frame.getMethodName());
        }
    }

    private static String sha256(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 must be available", exception);
        }
    }
}
