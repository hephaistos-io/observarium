package io.hephaistos.observarium;

import io.hephaistos.observarium.event.Severity;
import java.util.Map;

/**
 * Test fixture for issue #15's bytecode acceptance criterion.
 *
 * <p>This class calls only the {@code report(...)} overloads on {@link Observarium} — never {@code
 * captureException} — and does not import or otherwise reference {@link
 * java.util.concurrent.CompletableFuture} anywhere in its source. {@link
 * ObservariumReportBytecodeTest} inspects this class's compiled {@code .class} file to verify that
 * no reference to {@code CompletableFuture} was emitted into its constant pool, which is only
 * possible if {@code report(...)} truly returns {@code void} at the call site.
 *
 * <p>Keep this class's body limited to {@code report(...)} calls — adding any other reference to
 * {@code CompletableFuture} (directly or via a lambda/method-reference shape that captures one)
 * would invalidate the bytecode test.
 */
final class ReportOnlyCaller {

  private ReportOnlyCaller() {}

  static void callAll(Observarium observarium, Throwable throwable) {
    observarium.report(throwable);
    observarium.report(throwable, Severity.WARNING);
    observarium.report(throwable, Severity.ERROR, Map.of("k", "v"));
  }
}
