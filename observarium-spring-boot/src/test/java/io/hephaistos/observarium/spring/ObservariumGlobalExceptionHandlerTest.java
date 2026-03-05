package io.hephaistos.observarium.spring;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import io.hephaistos.observarium.Observarium;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ObservariumGlobalExceptionHandler}.
 *
 * <p>Verifies that {@code handleException} delegates to {@link Observarium#captureException} and
 * then re-throws the original exception unchanged.
 */
@ExtendWith(MockitoExtension.class)
class ObservariumGlobalExceptionHandlerTest {

  @Mock private Observarium observarium;

  @Test
  void handleExceptionCapturesViaObservariumThenRethrows() {
    ObservariumGlobalExceptionHandler handler = new ObservariumGlobalExceptionHandler(observarium);

    RuntimeException ex = new RuntimeException("something went wrong");

    assertThatThrownBy(() -> handler.handleException(ex)).isSameAs(ex);

    verify(observarium).captureException(ex);
  }

  @Test
  void handleExceptionRethrowsCheckedExceptionUnchanged() throws Exception {
    ObservariumGlobalExceptionHandler handler = new ObservariumGlobalExceptionHandler(observarium);

    Exception checked = new Exception("checked problem");

    assertThatThrownBy(() -> handler.handleException(checked)).isSameAs(checked);

    verify(observarium).captureException(checked);
  }
}
