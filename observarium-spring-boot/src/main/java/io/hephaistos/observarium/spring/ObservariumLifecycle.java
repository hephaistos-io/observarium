package io.hephaistos.observarium.spring;

import io.hephaistos.observarium.Observarium;
import org.springframework.boot.web.context.WebServerGracefulShutdownLifecycle;
import org.springframework.context.SmartLifecycle;

/**
 * Drains {@link Observarium} via {@link SmartLifecycle} instead of a bean {@code destroyMethod}.
 *
 * <p>Bean destruction ({@code destroyMethod = "shutdown"}) runs during {@code destroySingletons()},
 * which happens strictly after every {@link SmartLifecycle} bean — including {@link
 * WebServerGracefulShutdownLifecycle} — has already finished stopping. That serializes the two
 * drains: the request drain completes first, then Observarium's drain adds on top of it.
 *
 * <p>This class shares {@link WebServerGracefulShutdownLifecycle#SMART_LIFECYCLE_PHASE} exactly.
 * Spring's {@code DefaultLifecycleProcessor} groups {@code SmartLifecycle} beans by phase and stops
 * every bean within a phase concurrently, each on its own thread, waiting for the whole phase
 * before moving to the next one. Sharing the phase means the queue drain and the in-flight request
 * drain run side by side, so the wall-clock cost of shutting down is {@code max(request drain,
 * observarium drain)} rather than their sum — see {@code docs/configuration.md} for how to size the
 * two timeouts together.
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
