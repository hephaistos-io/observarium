package io.hephaistos.observarium.spring;

import io.hephaistos.observarium.Observarium;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Spring MVC {@link ControllerAdvice} that captures all unhandled exceptions via Observarium and
 * re-throws them so that Spring's normal error handling pipeline continues.
 *
 * <p>Ordered at {@link Ordered#LOWEST_PRECEDENCE} so that application-defined
 * {@code @ExceptionHandler} methods take precedence. Only activated when {@code DispatcherServlet}
 * is on the classpath, i.e. in a Spring MVC application.
 *
 * <p><strong>This is a last-resort capture point, not a guaranteed one.</strong> Spring's {@code
 * ExceptionHandlerExceptionResolver} resolves an exception by walking its advice cache in
 * declaration order and invoking the first matching handler — it does not chain multiple advices.
 * If the host application defines its own catch-all advice (a {@code @ExceptionHandler(Exception
 * .class)} or a {@code ResponseEntityExceptionHandler} subclass), that advice — not this one — is
 * the one that runs, and the host's own advice is never asked to also report through Observarium.
 * The auto-configuration therefore only registers this bean when no application-defined
 * {@code @ControllerAdvice} bean is already present (see {@code ConditionalOnMissingBean(annotation
 * = ControllerAdvice.class)} on {@code
 * ObservariumAutoConfiguration#observariumGlobalExceptionHandler}), and can be disabled outright
 * via {@code observarium.mvc.advice-enabled=false}.
 *
 * <p>When this handler does win, it re-throws after capturing, so the request falls through to
 * Spring Boot's default {@code BasicErrorController} — bypassing whatever custom error rendering
 * (e.g. an RFC 9457 {@code application/problem+json} contract) the host application would otherwise
 * have applied. Applications that own their error handling should not rely on this class at all:
 * call {@link Observarium#captureException(Throwable)} directly from their own
 * {@code @ControllerAdvice}.
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
