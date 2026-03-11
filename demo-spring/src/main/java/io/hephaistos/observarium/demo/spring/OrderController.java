package io.hephaistos.observarium.demo.spring;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.event.Severity;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {

  private final Observarium observarium;

  public OrderController(Observarium observarium) {
    this.observarium = observarium;
  }

  @GetMapping("/orders/{id}")
  public ResponseEntity<Map<String, Object>> getOrder(@PathVariable String id) {
    try {
      long orderId = Long.parseLong(id);
      if (orderId <= 0) {
        throw new IllegalArgumentException("Order ID must be positive, got: " + orderId);
      }
      return ResponseEntity.ok(Map.of("id", orderId, "status", "shipped"));
    } catch (Exception e) {
      observarium.captureException(
          e, Severity.ERROR, Map.of("endpoint", "/orders/" + id, "component", "order-service"));
      String message = Objects.requireNonNullElse(e.getMessage(), e.getClass().getName());
      return ResponseEntity.badRequest().body(Map.of("error", message));
    }
  }
}
