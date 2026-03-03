package io.hephaistos.observarium.fingerprint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Set;

public class DefaultExceptionFingerprinter implements ExceptionFingerprinter {

    private static final int MAX_CAUSE_DEPTH = 10;

    @Override
    public String fingerprint(Throwable throwable) {
        var sb = new StringBuilder();
        appendExceptionFrames(sb, throwable);

        // Walk the cause chain with cycle detection and depth limit.
        Set<Throwable> seen = Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        seen.add(throwable);
        Throwable cause = throwable.getCause();
        int depth = 0;
        while (cause != null && depth < MAX_CAUSE_DEPTH && seen.add(cause)) {
            sb.append("\ncaused_by:");
            appendExceptionFrames(sb, cause);
            cause = cause.getCause();
            depth++;
        }
        return sha256(sb.toString());
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
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available", e);
        }
    }
}
