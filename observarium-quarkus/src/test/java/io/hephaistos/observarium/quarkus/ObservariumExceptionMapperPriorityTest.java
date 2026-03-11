package io.hephaistos.observarium.quarkus;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for {@link ObservariumExceptionMapper}'s JAX-RS provider annotations.
 *
 * <p>The mapper is intended as a last-resort fallback: it catches all {@link Exception} subtypes at
 * a low priority so application-defined mappers for specific exception types take precedence. These
 * tests verify the annotation contract that makes this work, without requiring a running JAX-RS
 * container.
 *
 * <p>Full HTTP-level provider priority resolution requires a JAX-RS runtime (Quarkus 3.25+ with
 * Gradle plugin support). These contract tests provide confidence that the annotations are correct
 * for the intended dispatch behaviour.
 */
class ObservariumExceptionMapperPriorityTest {

  // ---------------------------------------------------------------------------
  // @Priority annotation
  // ---------------------------------------------------------------------------

  @Test
  void mapper_hasPriorityAnnotation_withExpectedValue() {
    Priority priority = ObservariumExceptionMapper.class.getAnnotation(Priority.class);

    assertThat(priority).as("@Priority annotation must be present").isNotNull();
    assertThat(priority.value())
        .as("Priority must be USER + 1000 so app mappers take precedence")
        .isEqualTo(Priorities.USER + 1000);
  }

  @Test
  void priorityValue_isHigherThanDefaultUserPriority() {
    Priority priority = ObservariumExceptionMapper.class.getAnnotation(Priority.class);

    // A higher numeric priority value means lower precedence in JAX-RS.
    // USER = 5000; this mapper uses 6000, so it is outranked by any mapper
    // at the default USER priority or below.
    assertThat(priority.value()).isGreaterThan(Priorities.USER);
  }

  // ---------------------------------------------------------------------------
  // @Provider annotation — required for JAX-RS discovery
  // ---------------------------------------------------------------------------

  @Test
  void mapper_hasProviderAnnotation() {
    assertThat(ObservariumExceptionMapper.class.getAnnotation(Provider.class))
        .as("@Provider must be present for JAX-RS auto-discovery")
        .isNotNull();
  }

  // ---------------------------------------------------------------------------
  // @ApplicationScoped — required for CDI managed lifecycle
  // ---------------------------------------------------------------------------

  @Test
  void mapper_hasApplicationScopedAnnotation() {
    assertThat(ObservariumExceptionMapper.class.getAnnotation(ApplicationScoped.class))
        .as("@ApplicationScoped must be present for CDI singleton lifecycle")
        .isNotNull();
  }

  // ---------------------------------------------------------------------------
  // Generic type parameter — must be Exception (catch-all)
  // ---------------------------------------------------------------------------

  @Test
  void mapper_implementsExceptionMapperForException_notASpecificSubtype() {
    // Verify the mapper implements ExceptionMapper<Exception>, making it the broadest
    // possible catch-all. A more specific type (e.g. ExceptionMapper<RuntimeException>)
    // would miss checked exceptions.
    var exceptionMapperInterface =
        Arrays.stream(ObservariumExceptionMapper.class.getGenericInterfaces())
            .filter(t -> t instanceof ParameterizedType)
            .map(t -> (ParameterizedType) t)
            .filter(t -> t.getRawType() == ExceptionMapper.class)
            .findFirst();

    assertThat(exceptionMapperInterface).isPresent();
    assertThat(exceptionMapperInterface.get().getActualTypeArguments()[0])
        .as("Generic type must be Exception (catch-all), not a specific subtype")
        .isEqualTo(Exception.class);
  }
}
