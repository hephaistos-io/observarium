package io.hephaistos.observarium.spring;

import io.hephaistos.observarium.Observarium;
import org.springframework.boot.web.context.WebServerGracefulShutdownLifecycle;
import org.springframework.context.SmartLifecycle;

/**
 * Drains {@link Observarium} via {@link SmartLifecycle} instead of a bean {@code destroyMethod}.
 *
 * <p>Bean destruction runs after every {@link SmartLifecycle} bean has stopped, which would
 * serialize this drain behind the web server's request drain. Sharing {@link
 * WebServerGracefulShutdownLifecycle#SMART_LIFECYCLE_PHASE} instead makes the two run concurrently
 * — Spring stops all beans in a phase in parallel — so shutdown costs {@code max(request drain,
 * observarium drain)} rather than their sum. See {@code docs/configuration.md} for sizing both.
 */
class ObservariumLifecycle implements SmartLifecycle {

  private final Observarium observarium;
  private volatile boolean running;

  ObservariumLifecycle(Observarium observarium) {
    this.observarium = observarium;
  }

  @Override
  public void start() {
    running = true;
  }

  @Override
  public void stop() {
    try {
      observarium.shutdown();
    } finally {
      running = false;
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public int getPhase() {
    return WebServerGracefulShutdownLifecycle.SMART_LIFECYCLE_PHASE;
  }
}
