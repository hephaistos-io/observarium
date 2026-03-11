package io.hephaistos.observarium.demo.quarkus;

import io.hephaistos.observarium.Observarium;
import io.hephaistos.observarium.event.Severity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.Objects;

@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
public class OrderResource {

  @Inject Observarium observarium;

  @GET
  @Path("/{id}")
  public Response getOrder(@PathParam("id") String id) {
    try {
      long orderId = Long.parseLong(id);
      if (orderId <= 0) {
        throw new IllegalArgumentException("Order ID must be positive, got: " + orderId);
      }
      return Response.ok(Map.of("id", orderId, "status", "shipped")).build();
    } catch (Exception e) {
      observarium.captureException(
          e, Severity.ERROR, Map.of("endpoint", "/orders/" + id, "component", "order-service"));
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(
              Map.of("error", Objects.requireNonNullElse(e.getMessage(), e.getClass().getName())))
          .build();
    }
  }
}
