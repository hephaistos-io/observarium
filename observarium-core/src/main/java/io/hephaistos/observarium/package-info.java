/**
 * Root package of the Observarium exception-reporting library.
 *
 * <p>The primary entry point for consumers is {@link io.hephaistos.observarium.Observarium}, which
 * provides the {@code captureException} API and the fluent {@code Builder} for configuring the
 * reporting pipeline. {@link io.hephaistos.observarium.ObservariumConfig} exposes read-only runtime
 * configuration after construction.
 */
package io.hephaistos.observarium;
