package io.hephaistos.observarium.quarkus;

import io.hephaistos.observarium.Observarium;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * JAX-RS {@link ExceptionMapper} that captures all unhandled exceptions via Observarium and returns
 * an HTTP 500 response.
 *
 * <p>Registered with {@link Priority}{@code (Priorities.USER + 1000)} so that application-defined
 * mappers with lower priority values take precedence and this handler acts only as a last-resort
 * fallback.
 *
 * <p>Only activated when {@code jakarta.ws.rs-api} is on the classpath (i.e. in a JAX-RS
 * application). Quarkus REST (RESTEasy Reactive or RESTEasy Classic) will automatically discover
 * this provider via CDI scanning.
 */
@Provider
@ApplicationScoped
@Priority(Priorities.USER + 1000)
public class ObservariumExceptionMapper implements ExceptionMapper<Exception> {

  @Inject Observarium observarium;

  @Override
  public Response toResponse(Exception exception) {
    if (exception instanceof jakarta.ws.rs.WebApplicationException webAppException) {
      if (webAppException.getResponse().getStatus() >= 500) {
        observarium.captureException(exception);
      }
      return webAppException.getResponse();
    }
    observarium.captureException(exception);
    return Response.serverError().entity("An unexpected error occurred").build();
  }
}
