package io.hephaistos.observarium.fingerprint;

public interface ExceptionFingerprinter {

    String fingerprint(Throwable throwable);
}
