package io.hephaistos.observarium.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.hephaistos.observarium.Observarium;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ObservariumExceptionMapper}.
 *
 * <p>RESTEasy core is on the test classpath to provide the JAX-RS {@code RuntimeDelegate} SPI,
 * which allows {@link Response#serverError()} to be called in a plain JUnit 5 environment without a
 * running Quarkus or servlet container.
 *
 * <p>The {@code @Inject} field is populated reflectively to avoid requiring a full CDI container.
 */
@ExtendWith(MockitoExtension.class)
class ObservariumExceptionMapperTest {

  @Mock private Observarium observarium;

  private ObservariumExceptionMapper mapper;

  @BeforeEach
  void setUp() throws Exception {
    mapper = new ObservariumExceptionMapper();
    Field field = ObservariumExceptionMapper.class.getDeclaredField("observarium");
    field.setAccessible(true);
    field.set(mapper, observarium);
  }

  // ---------------------------------------------------------------------------
  // captureException delegation
  // ---------------------------------------------------------------------------

  @Test
  void toResponse_capturesException_viaObservarium() {
    Exception exception = new RuntimeException("something broke");

    mapper.toResponse(exception);

    verify(observarium).captureException(exception);
  }

  @Test
  void toResponse_handlesRuntimeException() {
    RuntimeException runtimeException = new IllegalArgumentException("bad argument");

    mapper.toResponse(runtimeException);

    verify(observarium).captureException(runtimeException);
  }

  // ---------------------------------------------------------------------------
  // Response structure
  // ---------------------------------------------------------------------------

  @Test
  void toResponse_returns500Status() {
    Exception exception = new RuntimeException("server failure");

    Response response = mapper.toResponse(exception);

    assertThat(response.getStatus()).isEqualTo(500);
  }

  @Test
  void toResponse_returnsErrorMessage() {
    Exception exception = new RuntimeException("server failure");

    Response response = mapper.toResponse(exception);

    assertThat(response.getEntity()).isEqualTo("An unexpected error occurred");
  }
}
