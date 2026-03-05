/**
 * Data model for exception events flowing through the reporting pipeline.
 *
 * <p>An {@link io.hephaistos.observarium.event.ExceptionEvent} is the fully-assembled, scrubbed
 * representation of a captured exception that posting services receive. {@link
 * io.hephaistos.observarium.event.Severity} lets callers classify exceptions so that issue trackers
 * can prioritise them accordingly.
 */
package io.hephaistos.observarium.event;
