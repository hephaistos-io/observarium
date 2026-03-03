package io.hephaistos.observarium.trace;

import org.slf4j.MDC;

/**
 * {@link TraceContextProvider} that reads trace and span IDs from the SLF4J MDC.
 *
 * <p>Use this implementation when your application's tracing infrastructure (OpenTelemetry,
 * Brave/Zipkin, Spring Cloud Sleuth, etc.) propagates trace context through the SLF4J MDC.
 * Most tracing agents do this automatically for servlet-based and reactive web frameworks.
 *
 * <p>Because MDC is thread-local, this provider returns the IDs associated with the thread
 * that catches the exception. If exceptions are reported from a thread that does not inherit
 * MDC context (e.g. a raw {@link java.util.concurrent.ExecutorService} without MDC propagation),
 * both methods will return {@code null}. In that case, configure your executor with an
 * MDC-propagating wrapper, or supply a custom {@link TraceContextProvider}.
 */
public class MdcTraceContextProvider implements TraceContextProvider {

    private final String traceIdKey;
    private final String spanIdKey;

    /**
     * Creates a provider that reads trace context from the MDC keys {@code "trace_id"} and
     * {@code "span_id"}.
     *
     * <p>These are the conventional key names used by OpenTelemetry's SLF4J MDC bridge and
     * compatible agents. Use the two-arg constructor if your setup uses different key names.
     */
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
