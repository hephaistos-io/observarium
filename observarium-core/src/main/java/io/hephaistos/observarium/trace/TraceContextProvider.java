package io.hephaistos.observarium.trace;

public interface TraceContextProvider {

    String getTraceId();

    String getSpanId();
}
