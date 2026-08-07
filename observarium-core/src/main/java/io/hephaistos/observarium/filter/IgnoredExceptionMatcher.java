package io.hephaistos.observarium.filter;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Builds {@link Predicate}s for {@link io.hephaistos.observarium.Observarium.Builder#ignoreIf} that
 * match a throwable by fully-qualified class name, including its supertypes.
 *
 * <p>Used by the Spring and Quarkus modules to implement {@code observarium.ignored-exceptions}: a
 * list of FQCNs is matched against the throwable's class, its superclasses, and every interface in
 * that hierarchy — so listing a supertype (e.g. a domain base exception) also suppresses its
 * subclasses without the caller having to enumerate every one.
 *
 * <p>Matching is done purely by class name string comparison over the throwable's actual runtime
 * type hierarchy — no class loading of the configured names is attempted, so a typo in the
 * configured list simply never matches rather than failing at startup.
 */
public final class IgnoredExceptionMatcher {

  private IgnoredExceptionMatcher() {}

  /**
   * Returns a predicate that matches a throwable whose class, or any superclass or implemented
   * interface, has one of the given fully-qualified names.
   *
   * @param fullyQualifiedNames the configured class names; {@code null} or empty yields a predicate
   *     that never matches
   * @return a predicate suitable for {@code Observarium.Builder#ignoreIf}
   */
  public static Predicate<Throwable> byFullyQualifiedNames(List<String> fullyQualifiedNames) {
    if (fullyQualifiedNames == null || fullyQualifiedNames.isEmpty()) {
      return throwable -> false;
    }
    Set<String> names = Set.copyOf(fullyQualifiedNames);
    return throwable -> throwable != null && matchesAny(throwable.getClass(), names);
  }

  private static boolean matchesAny(Class<?> type, Set<String> names) {
    Deque<Class<?>> toVisit = new ArrayDeque<>();
    Set<Class<?>> visited = new HashSet<>();
    toVisit.add(type);

    while (!toVisit.isEmpty()) {
      Class<?> current = toVisit.poll();
      if (current == null || !visited.add(current)) {
        continue;
      }
      if (names.contains(current.getName())) {
        return true;
      }
      Class<?> superclass = current.getSuperclass();
      if (superclass != null) {
        toVisit.add(superclass);
      }
      toVisit.addAll(Arrays.asList(current.getInterfaces()));
    }
    return false;
  }
}
