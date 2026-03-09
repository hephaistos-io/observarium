package io.hephaistos.observarium.spring;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.hephaistos.observarium.Observarium;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Integration tests for {@link ObservariumGlobalExceptionHandler} using MockMvc.
 *
 * <p>Verifies that the handler intercepts exceptions thrown by controllers, delegates to {@link
 * Observarium#captureException}, and re-throws so that Spring's normal error pipeline continues.
 *
 * <p>Because the handler re-throws the exception after capturing it, the {@code DispatcherServlet}
 * has no further {@code @ExceptionHandler} to resolve it. MockMvc surfaces this as a {@link
 * ServletException} from {@code perform()}. Tests that verify the capture behaviour use {@code
 * assertThatThrownBy} to invoke the request and then verify the Mockito interaction.
 *
 * <p>{@code @SpringBootTest} with explicit {@code classes} is used rather than {@code @WebMvcTest}
 * because the inner {@link FailingController} must be explicitly included in the application
 * context, which {@code @WebMvcTest} cannot do for inner static classes in library modules that
 * lack a top-level {@code @SpringBootApplication}.
 */
@SpringBootTest(
    classes = {
      ObservariumGlobalExceptionHandlerMockMvcTest.TestConfig.class,
      ObservariumGlobalExceptionHandlerMockMvcTest.FailingController.class
    })
@AutoConfigureMockMvc
class ObservariumGlobalExceptionHandlerMockMvcTest {

  /**
   * Minimal Spring Boot configuration that wires the handler under test. Uses {@code @Import} to
   * pull in the handler directly rather than relying on auto-configuration classpath scanning,
   * which keeps the test context small and focused.
   */
  @SpringBootConfiguration
  @Import(ObservariumGlobalExceptionHandler.class)
  static class TestConfig {}

  @Autowired private MockMvc mockMvc;

  @MockitoBean private Observarium observarium;

  /** Minimal controller that throws controllable exceptions for test scenarios. */
  @RestController
  static class FailingController {

    @GetMapping("/fail-runtime")
    String failRuntime() {
      throw new RuntimeException("test boom");
    }

    @GetMapping("/fail-checked")
    String failChecked() throws Exception {
      throw new Exception("checked boom");
    }

    @GetMapping("/ok")
    String ok() {
      return "ok";
    }
  }

  @Test
  void runtimeExceptionFromController_isCapturedByObservarium() {
    assertThatThrownBy(
            () -> mockMvc.perform(MockMvcRequestBuilders.get("/fail-runtime")).andReturn())
        .isInstanceOf(ServletException.class)
        .hasCauseInstanceOf(RuntimeException.class)
        .getCause()
        .hasMessage("test boom");

    verify(observarium).captureException(any(RuntimeException.class));
  }

  @Test
  void checkedExceptionFromController_isCapturedByObservarium() {
    assertThatThrownBy(
            () -> mockMvc.perform(MockMvcRequestBuilders.get("/fail-checked")).andReturn())
        .isInstanceOf(ServletException.class)
        .hasCauseInstanceOf(Exception.class)
        .getCause()
        .hasMessage("checked boom");

    verify(observarium).captureException(any(Exception.class));
  }

  @Test
  void runtimeExceptionFromController_causeIsTheOriginalException() {
    assertThatThrownBy(
            () -> mockMvc.perform(MockMvcRequestBuilders.get("/fail-runtime")).andReturn())
        .isInstanceOf(ServletException.class)
        .satisfies(
            wrapped -> {
              Throwable cause = wrapped.getCause();
              org.assertj.core.api.Assertions.assertThat(cause)
                  .isInstanceOf(RuntimeException.class)
                  .hasMessage("test boom");
            });
  }

  @Test
  void successfulRequest_doesNotInvokeObservarium() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/ok")).andReturn();

    verify(observarium, never()).captureException(any());
  }
}
