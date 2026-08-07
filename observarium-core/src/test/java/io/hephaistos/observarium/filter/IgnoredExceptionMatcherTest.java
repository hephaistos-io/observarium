package io.hephaistos.observarium.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class IgnoredExceptionMatcherTest {

  @Test
  void emptyList_neverMatches() {
    Predicate<Throwable> predicate = IgnoredExceptionMatcher.byFullyQualifiedNames(List.of());
    assertFalse(predicate.test(new RuntimeException()));
  }

  @Test
  void nullList_neverMatches() {
    Predicate<Throwable> predicate = IgnoredExceptionMatcher.byFullyQualifiedNames(null);
    assertFalse(predicate.test(new RuntimeException()));
  }

  @Test
  void exactClassNameMatch() {
    Predicate<Throwable> predicate =
        IgnoredExceptionMatcher.byFullyQualifiedNames(List.of("java.lang.IllegalStateException"));
    assertTrue(predicate.test(new IllegalStateException()));
  }

  @Test
  void subclassOfListedType_matches() {
    // FileNotFoundException extends IOException.
    Predicate<Throwable> predicate =
        IgnoredExceptionMatcher.byFullyQualifiedNames(List.of("java.io.IOException"));
    assertTrue(predicate.test(new java.io.FileNotFoundException("missing")));
  }

  @Test
  void unrelatedType_doesNotMatch() {
    Predicate<Throwable> predicate =
        IgnoredExceptionMatcher.byFullyQualifiedNames(List.of("java.lang.IllegalStateException"));
    assertFalse(predicate.test(new IllegalArgumentException()));
  }

  @Test
  void matchesConfiguredInterfaceImplementedTransitively() {
    // A custom subclass implementing an interface indirectly via a superclass.
    class Custom extends IOException {}
    Predicate<Throwable> predicate =
        IgnoredExceptionMatcher.byFullyQualifiedNames(List.of("java.io.IOException"));
    assertTrue(predicate.test(new Custom()));
  }
}
