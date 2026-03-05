package io.hephaistos.observarium.trace;

/**
 * SPI for supplying distributed trace context to include in exception reports.
 *
 * <p>When an exception is captured, Observarium calls this provider to attach the current trace and
 * span IDs to the {@link io.hephaistos.observarium.event.ExceptionEvent}. Those IDs are then
 * included in the issue body, allowing an engineer to jump directly from an open issue to the
 * corresponding trace in their observability platform.
 *
 * <p>Both methods may return {@code null} — tracing context is optional. Observarium omits the
 * fields from the event when they are null.
 *
 * <p>The default implementation is {@link MdcTraceContextProvider}. Provide a custom implementation
 * when trace context is stored somewhere other than the SLF4J MDC — for example, in a thread-local
 * supplied by your tracing SDK, or in a reactive context propagator.
 *
 * <p><b>Thread-safety:</b> both methods must be safe to call from any thread, since Observarium may
 * capture exceptions from threads other than the one that initiated the request.
 */
public interface TraceContextProvider {

  String getTraceId();

  String getSpanId();
}
