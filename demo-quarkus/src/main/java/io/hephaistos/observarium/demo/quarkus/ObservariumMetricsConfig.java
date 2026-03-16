package io.hephaistos.observarium.demo.quarkus;

import io.hephaistos.observarium.micrometer.ObservariumMeterBinder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * CDI producer that wires {@link ObservariumMeterBinder} into the Quarkus application.
 *
 * <p>Quarkus does not support classpath-conditional beans in plain library JARs (its bean discovery
 * is build-time via Jandex, not runtime), so the binder must be declared explicitly as a CDI bean.
 * The {@link io.hephaistos.observarium.quarkus.ObservariumProducer} picks it up via {@code
 * Instance<ObservariumListener>}, and Quarkus Micrometer auto-calls {@code bindTo()} since it
 * implements {@link io.micrometer.core.instrument.binder.MeterBinder}.
 */
@ApplicationScoped
public class ObservariumMetricsConfig {

  @Produces
  @ApplicationScoped
  public ObservariumMeterBinder observariumMeterBinder() {
    return new ObservariumMeterBinder();
  }
}
