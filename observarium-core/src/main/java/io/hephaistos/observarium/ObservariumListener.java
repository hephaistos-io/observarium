package io.hephaistos.observarium;

import io.hephaistos.observarium.event.Severity;
import java.util.function.Supplier;

/**
 * Callback interface for observing the internal lifecycle of the Observarium processing pipeline.
 *
 * <p>Implementations can use these callbacks to collect metrics, emit logs, or drive alerting
 * without modifying the core processing logic. All methods have no-op defaults so that
 * implementations only need to override the events they care about.
 *
 * <p>Callbacks are invoked synchronously on the thread where the event occurs:
 *
 * <ul>
 *   <li>{@link #onExceptionCaptured} and {@link #onExceptionDropped} are called on the caller's
 *       thread (the thread that invoked {@link Observarium#captureException}).
 *   <li>{@link #onPostingCompleted} is called on the background worker thread during exception
 *       processing.
 * </ul>
 *
 * Implementations must be thread-safe and must not throw exceptions — any exception thrown from a
 * callback is caught and logged but otherwise ignored, so it never disrupts the host application.
 */
public interface ObservariumListener {

  /**
   * Called when an exception is successfully enqueued for processing.
   *
   * @param severity the severity assigned to the captured exception
   */
  default void onExceptionCaptured(Severity severity) {}

  /**
   * Called when an exception is dropped because the internal queue is full.
   *
   * <p>This signals backpressure — the posting services cannot keep up with the exception capture
   * rate.
   */
  default void onExceptionDropped() {}

  /**
   * Called after a posting service completes processing (either creating a new issue, commenting on
   * an existing one, or failing).
   *
   * @param serviceName the {@link io.hephaistos.observarium.posting.PostingService#name()} of the
   *     service
   * @param duplicate {@code true} if a duplicate issue was found and a comment was added, {@code
   *     false} if a new issue was created
   * @param success {@code true} if the posting service returned a successful {@link
   *     io.hephaistos.observarium.posting.PostingResult}; {@code false} when the result indicates
   *     failure or when an exception was thrown
   * @param durationNanos wall-clock time in nanoseconds for the posting service call (includes
   *     duplicate search + create/comment)
   */
  default void onPostingCompleted(
      String serviceName, boolean duplicate, boolean success, long durationNanos) {}

  /**
   * Called when a duplicate comment is suppressed because the issue has already reached the
   * configured comment limit.
   *
   * <p>This signals that the exception is still occurring but Observarium has stopped adding
   * comments to avoid flooding the issue tracker. Monitor the {@code observarium.comments.dropped}
   * metric for ongoing occurrence counts.
   *
   * @param serviceName the {@link io.hephaistos.observarium.posting.PostingService#name()} of the
   *     service whose comment was dropped
   */
  default void onCommentDropped(String serviceName) {}

  /**
   * Called once during {@link Observarium} construction to provide access to the current queue
   * depth.
   *
   * <p>Implementations that expose a queue-size gauge (e.g., Micrometer integration) should store
   * this supplier and sample it when the monitoring system polls for the current value.
   *
   * <p><strong>Lifecycle note:</strong> the supplier holds a reference to the internal queue. After
   * {@link Observarium#shutdown()} the supplier will return {@code 0} but the reference is
   * retained. If the {@code Observarium} instance is rebuilt (e.g., during a Spring context
   * refresh), the previous supplier and its queue remain reachable until the listener itself is
   * garbage collected.
   *
   * @param queueSizeSupplier returns the current number of items in the processing queue; never
   *     {@code null}
   */
  default void onQueueSizeAvailable(Supplier<Integer> queueSizeSupplier) {}
}
