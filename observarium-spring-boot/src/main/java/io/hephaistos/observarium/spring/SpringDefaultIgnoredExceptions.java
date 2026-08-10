package io.hephaistos.observarium.spring;

import java.util.function.Predicate;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The default {@code ignoreIf} predicate installed by {@link ObservariumAutoConfiguration} when
 * Spring MVC is on the classpath: suppress reporting for exceptions the framework already
 * classifies as a client error.
 *
 * <p>Loaded only when {@code org.springframework.web.ErrorResponse} resolves — guarded by an
 * explicit {@code ClassUtils.isPresent} check at the call site — so referencing Spring MVC types
 * here never triggers a {@link NoClassDefFoundError} in a non-web Spring Boot application that
 * pulls in this module without {@code spring-web} on the runtime classpath.
 *
 * <p>Matches:
 *
 * <ul>
 *   <li>{@link ErrorResponse} implementations — the standard RFC 7807 problem-detail contract
 *       Spring MVC uses for its own client-error exceptions (e.g. {@code
 *       MethodArgumentNotValidException}, {@code MethodArgumentTypeMismatchException}).
 *   <li>Any exception annotated {@link ResponseStatus} with a {@code 4xx} status.
 *   <li>{@link BindException} and its subclasses, which includes {@code
 *       MethodArgumentNotValidException} — bean-validation failures are a 400, not a defect.
 * </ul>
 */
final class SpringDefaultIgnoredExceptions {

  private SpringDefaultIgnoredExceptions() {}

  static Predicate<Throwable> clientErrorPredicate() {
    return SpringDefaultIgnoredExceptions::isClientError;
  }

  private static boolean isClientError(Throwable throwable) {
    if (throwable instanceof ErrorResponse errorResponse
        && errorResponse.getStatusCode().is4xxClientError()) {
      return true;
    }
    if (throwable instanceof BindException) {
      // Covers org.springframework.web.bind.MethodArgumentNotValidException as well, since it
      // extends BindException.
      return true;
    }
    ResponseStatus responseStatus =
        AnnotatedElementUtils.findMergedAnnotation(throwable.getClass(), ResponseStatus.class);
    // code() and value() are aliases of the same attribute; code() is the canonical accessor.
    return responseStatus != null && responseStatus.code().is4xxClientError();
  }
}
