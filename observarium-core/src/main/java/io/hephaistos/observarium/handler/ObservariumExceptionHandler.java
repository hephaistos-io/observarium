package io.hephaistos.observarium.handler;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.event.Severity;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Thread.UncaughtExceptionHandler} that reports fatal uncaught exceptions through {@link
 * Observarium} before the JVM exits.
 *
 * <p>Because the JVM may terminate immediately after invoking this handler, {@link
 * #uncaughtException} blocks for up to {@link #FATAL_WAIT_SECONDS} seconds to give the reporting
 * pipeline time to deliver the event. Install this handler via {@link #install(Observarium)} to
 * preserve any existing default handler as a delegate, or construct it directly when more control
 * is needed.
 */
public class ObservariumExceptionHandler implements Thread.UncaughtExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ObservariumExceptionHandler.class);

  /**
   * Maximum number of seconds {@link #uncaughtException} will block while waiting for the fatal
   * exception report to be delivered. Kept short to avoid delaying JVM shutdown indefinitely when
   * posting services are unresponsive.
   */
  private static final long FATAL_WAIT_SECONDS = 5;

  private final Observarium observarium;
  private final Thread.UncaughtExceptionHandler delegate;

  public ObservariumExceptionHandler(Observarium observarium) {
    this(observarium, null);
  }

  /**
   * Creates a handler that reports to the given {@link Observarium} instance and then forwards to
   * an existing handler.
   *
   * @param observarium the Observarium instance to report exceptions through; must not be null
   * @param delegate an existing handler to invoke after reporting, so that prior behaviour (e.g.
   *     printing to stderr) is preserved; may be null
   */
  public ObservariumExceptionHandler(
      Observarium observarium, Thread.UncaughtExceptionHandler delegate) {
    this.observarium = observarium;
    this.delegate = delegate;
  }

  /**
   * Reports the uncaught exception at {@link Severity#FATAL} severity and blocks until delivery
   * completes or {@link #FATAL_WAIT_SECONDS} elapses. Blocking is necessary because the JVM may
   * exit as soon as this method returns, giving the async worker no time to finish. The configured
   * {@link #delegate} is always invoked afterwards, even if reporting fails.
   */
  @Override
  public void uncaughtException(Thread thread, Throwable exception) {
    try {
      // Block briefly to ensure the fatal exception report is delivered
      // before the JVM exits.
      observarium
          .captureException(exception, Severity.FATAL)
          .get(FATAL_WAIT_SECONDS, TimeUnit.SECONDS);
    } catch (Exception deliveryFailure) {
      log.error("Failed to deliver fatal exception report", deliveryFailure);
    }
    if (delegate != null) {
      delegate.uncaughtException(thread, exception);
    }
  }

  /**
   * Convenience method that installs this handler as the JVM-wide default uncaught exception
   * handler. Any handler already set via {@link Thread#setDefaultUncaughtExceptionHandler} is
   * preserved and will be called as a delegate after Observarium finishes reporting.
   *
   * @param observarium the Observarium instance to report exceptions through; must not be null
   */
  public static void install(Observarium observarium) {
    Thread.UncaughtExceptionHandler existing = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(
        new ObservariumExceptionHandler(observarium, existing));
  }
}
