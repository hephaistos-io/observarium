package io.hephaistos.observarium.fingerprint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class DefaultExceptionFingerprinter implements ExceptionFingerprinter {

    @Override
    public String fingerprint(Throwable throwable) {
        var sb = new StringBuilder();
        sb.append(throwable.getClass().getName());
        for (StackTraceElement frame : throwable.getStackTrace()) {
            sb.append('\n');
            sb.append(frame.getClassName());
            sb.append('#');
            sb.append(frame.getMethodName());
        }
        Throwable cause = throwable.getCause();
        while (cause != null) {
            sb.append("\ncaused_by:");
            sb.append(cause.getClass().getName());
            cause = cause.getCause();
        }
        return sha256(sb.toString());
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
