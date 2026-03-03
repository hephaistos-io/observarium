package io.hephaistos.observarium.trace;

import org.slf4j.MDC;

public class MdcTraceContextProvider implements TraceContextProvider {

    private final String traceIdKey;
    private final String spanIdKey;

    public MdcTraceContextProvider() {
        this("trace_id", "span_id");
    }

    public MdcTraceContextProvider(String traceIdKey, String spanIdKey) {
        this.traceIdKey = traceIdKey;
        this.spanIdKey = spanIdKey;
    }

    @Override
    public String getTraceId() {
        return MDC.get(traceIdKey);
    }

    @Override
    public String getSpanId() {
        return MDC.get(spanIdKey);
    }
}
