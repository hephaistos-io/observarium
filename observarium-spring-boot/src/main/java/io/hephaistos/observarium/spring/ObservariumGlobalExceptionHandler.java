package io.hephaistos.observarium.spring;

import io.hephaistos.observarium.Observarium;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Spring MVC {@link ControllerAdvice} that captures all unhandled exceptions via Observarium
 * and re-throws them so that Spring's normal error handling pipeline continues.
 *
 * <p>Ordered at {@link Ordered#LOWEST_PRECEDENCE} so that application-defined
 * {@code @ExceptionHandler} methods take precedence. Only activated when
 * {@code DispatcherServlet} is on the classpath, i.e. in a Spring MVC application.
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
     * Captures the exception asynchronously and re-throws it unchanged so that downstream
     * error handling (e.g. Spring Boot's {@code BasicErrorController}) still processes it.
     */
    @ExceptionHandler(Exception.class)
    public void handleException(Exception ex) throws Exception {
        observarium.captureException(ex);
        throw ex;
    }
}
