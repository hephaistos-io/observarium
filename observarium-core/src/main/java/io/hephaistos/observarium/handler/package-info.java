/**
 * Internal pipeline processing and uncaught exception handling.
 *
 * <p>{@link io.hephaistos.observarium.handler.ExceptionProcessor} is the internal orchestrator that
 * drives the fingerprint -&gt; scrub -&gt; post pipeline; it is not part of the public API. {@link
 * io.hephaistos.observarium.handler.ObservariumExceptionHandler} is the public-facing {@link
 * java.lang.Thread.UncaughtExceptionHandler} that consumers install to automatically report fatal,
 * unhandled exceptions before the JVM shuts down.
 */
package io.hephaistos.observarium.handler;
