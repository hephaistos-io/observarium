/**
 * Distributed tracing context integration for enriching exception events.
 *
 * <p>{@link io.hephaistos.observarium.trace.TraceContextProvider} defines the SPI for reading
 * the current trace and span IDs at the moment an exception is captured. The default
 * implementation, {@link io.hephaistos.observarium.trace.MdcTraceContextProvider}, reads from
 * SLF4J MDC, which is compatible with OpenTelemetry and Brave out of the box. Provide a custom
 * implementation via {@link io.hephaistos.observarium.Observarium.Builder#traceContextProvider}
 * when using a different propagation mechanism.
 */
package io.hephaistos.observarium.trace;
