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

  /**
   * Demonstrates <b>manual</b> exception capture — the controller catches the exception itself,
   * calls {@link Observarium#captureException}, and returns a controlled error response.
   */
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

  /**
   * Demonstrates <b>automatic</b> exception capture — the exception propagates unhandled and is
   * caught by {@link io.hephaistos.observarium.spring.ObservariumGlobalExceptionHandler}, which is
   * auto-configured at {@code @Order(LOWEST_PRECEDENCE)}. It captures the exception and re-throws
   * it, so Spring's normal error handling (e.g. {@code BasicErrorController}) still produces the
   * response. Application-defined {@code @ExceptionHandler} methods always take precedence.
   */
  @GetMapping("/orders/{id}/details")
  public ResponseEntity<Map<String, Object>> getOrderDetails(@PathVariable String id) {
    long orderId = Long.parseLong(id);
    if (orderId <= 0) {
      throw new IllegalArgumentException("Order ID must be positive, got: " + orderId);
    }
    return ResponseEntity.ok(
        Map.of("id", orderId, "status", "shipped", "item", "Widget", "quantity", 3));
  }
}
