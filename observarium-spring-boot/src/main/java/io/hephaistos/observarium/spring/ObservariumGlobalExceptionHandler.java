package io.hephaistos.observarium.spring;

import io.hephaistos.observarium.Observarium;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Spring MVC {@link ControllerAdvice} that captures unhandled exceptions via Observarium and
 * re-throws them so Spring's normal error handling continues. Only active when {@code
 * DispatcherServlet} is on the classpath.
 *
 * <p>This is a last-resort capture point. Spring resolves an exception with the first matching
 * advice and does not chain advices, so an application-defined catch-all advice wins over this one
 * — the auto-configuration therefore skips this bean when the application declares its own
 * {@code @ControllerAdvice}, and {@code observarium.mvc.advice-enabled=false} disables it outright.
 * Because it re-throws, the request falls through to Spring Boot's {@code BasicErrorController}
 * rather than any custom error rendering. Applications that own their error handling should call
 * {@link Observarium#captureException(Throwable)} from their own advice instead of relying on this
 * class.
 */
@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
public class ObservariumGlobalExceptionHandler {

  private final Observarium observarium;

  public ObservariumGlobalExceptionHandler(Observarium observarium) {
    this.observarium = observarium;
  }

  /**
   * Captures the exception asynchronously and re-throws it unchanged so that downstream error
   * handling (e.g. Spring Boot's {@code BasicErrorController}) still processes it.
   */
  @ExceptionHandler(Exception.class)
  public void handleException(Exception ex) throws Exception {
    observarium.captureException(ex);
    throw ex;
  }
}
