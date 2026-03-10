package io.hephaistos.observarium.handler;

import static org.junit.jupiter.api.Assertions.*;

import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.event.Severity;
import io.hephaistos.observarium.fingerprint.ExceptionFingerprinter;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import io.hephaistos.observarium.scrub.DataScrubber;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExceptionProcessorTest {

  // -----------------------------------------------------------------------
  // Minimal stub implementations — no Mockito required
  // -----------------------------------------------------------------------

  /** Fingerprinter that always returns a fixed hash-shaped string. */
  private static final ExceptionFingerprinter FIXED_FINGERPRINTER = throwable -> "a".repeat(64);

  /** Scrubber that returns text as-is (transparent). */
  private static final DataScrubber PASSTHROUGH_SCRUBBER = text -> text;

  /** Scrubber that prefixes every non-null string with "SCRUBBED:". */
  private static final DataScrubber MARKING_SCRUBBER =
      text -> text == null ? null : "SCRUBBED:" + text;

  // -----------------------------------------------------------------------
  // Recording PostingService stubs
  // -----------------------------------------------------------------------

  /** Records every call so tests can assert on interactions. */
  private static class RecordingPostingService implements PostingService {

    private final String serviceName;
    private final DuplicateSearchResult duplicateResult;

    final List<ExceptionEvent> createIssueCalls = new ArrayList<>();
    final List<String> commentIssueIds = new ArrayList<>();
    final List<ExceptionEvent> commentEventCalls = new ArrayList<>();

    RecordingPostingService(String name, DuplicateSearchResult duplicateResult) {
      this.serviceName = name;
      this.duplicateResult = duplicateResult;
    }

    @Override
    public String name() {
      return serviceName;
    }

    @Override
    public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
      return duplicateResult;
    }

    @Override
    public PostingResult createIssue(ExceptionEvent event) {
      createIssueCalls.add(event);
      return PostingResult.success("NEW-1", "https://tracker/NEW-1");
    }

    @Override
    public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
      commentIssueIds.add(externalIssueId);
      commentEventCalls.add(event);
      return PostingResult.success(externalIssueId, "https://tracker/" + externalIssueId);
    }
  }

  /** PostingService whose findDuplicate() throws an exception. */
  private static class FailingPostingService implements PostingService {

    private final String serviceName;
    final List<String> callLog = new ArrayList<>();

    FailingPostingService(String name) {
      this.serviceName = name;
    }

    @Override
    public String name() {
      return serviceName;
    }

    @Override
    public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
      callLog.add("findDuplicate");
      throw new RuntimeException("Network error from " + serviceName);
    }

    @Override
    public PostingResult createIssue(ExceptionEvent event) {
      callLog.add("createIssue");
      return PostingResult.success("X-1", "https://tracker/X-1");
    }

    @Override
    public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
      callLog.add("commentOnIssue");
      return PostingResult.success(externalIssueId, "https://tracker/" + externalIssueId);
    }
  }

  /**
   * PostingService that reports no duplicate (so createIssue is called) and then throws from
   * createIssue().
   */
  private static class CreateIssueThrowingService implements PostingService {

    private final String serviceName;

    CreateIssueThrowingService(String name) {
      this.serviceName = name;
    }

    @Override
    public String name() {
      return serviceName;
    }

    @Override
    public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
      return DuplicateSearchResult.notFound();
    }

    @Override
    public PostingResult createIssue(ExceptionEvent event) {
      throw new RuntimeException("createIssue failed on " + serviceName);
    }

    @Override
    public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
      return PostingResult.success(externalIssueId, "https://tracker/" + externalIssueId);
    }
  }

  /**
   * PostingService that reports a duplicate (so commentOnIssue is called) and then throws from
   * commentOnIssue().
   */
  private static class CommentOnIssueThrowingService implements PostingService {

    private static final String EXISTING_ID = "EXISTING-99";
    private final String serviceName;

    CommentOnIssueThrowingService(String name) {
      this.serviceName = name;
    }

    @Override
    public String name() {
      return serviceName;
    }

    @Override
    public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
      return DuplicateSearchResult.found(EXISTING_ID, "https://tracker/" + EXISTING_ID);
    }

    @Override
    public PostingResult createIssue(ExceptionEvent event) {
      return PostingResult.success("NEW-1", "https://tracker/NEW-1");
    }

    @Override
    public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
      throw new RuntimeException("commentOnIssue failed on " + serviceName);
    }
  }

  // -----------------------------------------------------------------------
  // Helper
  // -----------------------------------------------------------------------

  private static ExceptionProcessor processor(List<PostingService> services) {
    return new ExceptionProcessor(FIXED_FINGERPRINTER, PASSTHROUGH_SCRUBBER, services);
  }

  /** Calls process with pre-captured trace context (as Observarium now does eagerly). */
  private static List<PostingResult> process(
      ExceptionProcessor proc, Throwable throwable, Severity severity, Map<String, String> tags) {
    return proc.process(throwable, severity, tags, "test-thread", null, null);
  }

  private static List<PostingResult> processWithTrace(
      ExceptionProcessor proc,
      Throwable throwable,
      Severity severity,
      Map<String, String> tags,
      String traceId,
      String spanId) {
    return proc.process(throwable, severity, tags, "test-thread", traceId, spanId);
  }

  // -----------------------------------------------------------------------
  // Tests: routing (createIssue vs commentOnIssue)
  // -----------------------------------------------------------------------

  @Test
  void noDuplicateFound_callsCreateIssue() {
    RecordingPostingService service =
        new RecordingPostingService("svc", DuplicateSearchResult.notFound());
    ExceptionProcessor proc = processor(List.of(service));

    List<PostingResult> results =
        process(proc, new RuntimeException("boom"), Severity.ERROR, Map.of());

    assertEquals(1, results.size());
    assertTrue(results.get(0).success());
    assertEquals(1, service.createIssueCalls.size(), "createIssue must be called once");
    assertEquals(0, service.commentIssueIds.size(), "commentOnIssue must not be called");
  }

  @Test
  void duplicateFound_callsCommentOnIssueWithCorrectId() {
    String existingId = "ISSUE-42";
    RecordingPostingService service =
        new RecordingPostingService("svc", DuplicateSearchResult.found(existingId, "https://t/42"));
    ExceptionProcessor proc = processor(List.of(service));

    process(proc, new RuntimeException("dup"), Severity.WARNING, Map.of());

    assertEquals(0, service.createIssueCalls.size(), "createIssue must not be called");
    assertEquals(1, service.commentIssueIds.size(), "commentOnIssue must be called once");
    assertEquals(
        existingId,
        service.commentIssueIds.get(0),
        "commentOnIssue must receive the existing issue id");
  }

  // -----------------------------------------------------------------------
  // Tests: multiple services
  // -----------------------------------------------------------------------

  @Test
  void multiplePostingServices_allGetCalled() {
    RecordingPostingService svc1 =
        new RecordingPostingService("svc1", DuplicateSearchResult.notFound());
    RecordingPostingService svc2 =
        new RecordingPostingService("svc2", DuplicateSearchResult.notFound());

    ExceptionProcessor proc = processor(List.of(svc1, svc2));
    List<PostingResult> results =
        process(proc, new RuntimeException("multi"), Severity.ERROR, Map.of());

    assertEquals(2, results.size(), "One result per posting service");
    assertEquals(1, svc1.createIssueCalls.size(), "svc1 must receive createIssue");
    assertEquals(1, svc2.createIssueCalls.size(), "svc2 must receive createIssue");
  }

  @Test
  void failingService_doesNotPreventOtherServicesFromRunning() {
    FailingPostingService failingSvc = new FailingPostingService("failing");
    RecordingPostingService goodSvc =
        new RecordingPostingService("good", DuplicateSearchResult.notFound());

    ExceptionProcessor proc = processor(List.of(failingSvc, goodSvc));
    List<PostingResult> results =
        process(proc, new RuntimeException("oops"), Severity.ERROR, Map.of());

    assertEquals(2, results.size(), "Both services must produce a result");

    PostingResult failResult = results.get(0);
    assertFalse(failResult.success(), "Failed service result must be unsuccessful");
    assertTrue(
        failResult.errorMessage().contains("failing"),
        "Error message must reference the failing service name");

    PostingResult goodResult = results.get(1);
    assertTrue(goodResult.success(), "Good service must still succeed");
    assertEquals(1, goodSvc.createIssueCalls.size(), "Good service must still be called");
  }

  // -----------------------------------------------------------------------
  // Tests: event construction
  // -----------------------------------------------------------------------

  @Test
  void event_containsCorrectFingerprint() {
    RecordingPostingService service =
        new RecordingPostingService("svc", DuplicateSearchResult.notFound());
    ExceptionProcessor proc = processor(List.of(service));

    process(proc, new RuntimeException("x"), Severity.ERROR, Map.of());

    ExceptionEvent event = service.createIssueCalls.get(0);
    assertEquals("a".repeat(64), event.fingerprint());
  }

  @Test
  void event_messageIsScrubbedBeforeStorage() {
    RecordingPostingService service =
        new RecordingPostingService("svc", DuplicateSearchResult.notFound());
    ExceptionProcessor proc =
        new ExceptionProcessor(FIXED_FINGERPRINTER, MARKING_SCRUBBER, List.of(service));

    process(proc, new RuntimeException("sensitive data"), Severity.ERROR, Map.of());

    ExceptionEvent event = service.createIssueCalls.get(0);
    assertTrue(
        event.message().startsWith("SCRUBBED:"), "Message must be passed through the scrubber");
  }

  @Test
  void event_traceContextIsPopulated() {
    RecordingPostingService service =
        new RecordingPostingService("svc", DuplicateSearchResult.notFound());
    ExceptionProcessor proc =
        new ExceptionProcessor(FIXED_FINGERPRINTER, PASSTHROUGH_SCRUBBER, List.of(service));

    processWithTrace(
        proc, new RuntimeException("traced"), Severity.INFO, Map.of(), "trace-abc", "span-xyz");

    ExceptionEvent event = service.createIssueCalls.get(0);
    assertEquals("trace-abc", event.traceId());
    assertEquals("span-xyz", event.spanId());
  }

  @Test
  void event_severityIsPreserved() {
    RecordingPostingService service =
        new RecordingPostingService("svc", DuplicateSearchResult.notFound());
    ExceptionProcessor proc = processor(List.of(service));

    process(proc, new RuntimeException("warn"), Severity.WARNING, Map.of());

    ExceptionEvent event = service.createIssueCalls.get(0);
    assertEquals(Severity.WARNING, event.severity());
  }

  @Test
  void event_tagsArePreserved() {
    RecordingPostingService service =
        new RecordingPostingService("svc", DuplicateSearchResult.notFound());
    ExceptionProcessor proc = processor(List.of(service));

    Map<String, String> tags = Map.of("env", "production", "region", "us-east-1");
    process(proc, new RuntimeException("tagged"), Severity.ERROR, tags);

    ExceptionEvent event = service.createIssueCalls.get(0);
    assertEquals("production", event.tags().get("env"));
    assertEquals("us-east-1", event.tags().get("region"));
  }

  @Test
  void event_nullTagsDefaultToEmptyMap() {
    RecordingPostingService service =
        new RecordingPostingService("svc", DuplicateSearchResult.notFound());
    ExceptionProcessor proc = processor(List.of(service));

    process(proc, new RuntimeException("no tags"), Severity.ERROR, null);

    ExceptionEvent event = service.createIssueCalls.get(0);
    assertNotNull(event.tags());
    assertTrue(event.tags().isEmpty());
  }

  @Test
  void event_exceptionClassNameIsFullyQualified() {
    RecordingPostingService service =
        new RecordingPostingService("svc", DuplicateSearchResult.notFound());
    ExceptionProcessor proc = processor(List.of(service));

    process(proc, new IllegalArgumentException("bad"), Severity.ERROR, Map.of());

    ExceptionEvent event = service.createIssueCalls.get(0);
    assertEquals("java.lang.IllegalArgumentException", event.exceptionClass());
  }

  @Test
  void noPostingServices_returnsEmptyResultList() {
    ExceptionProcessor proc = processor(List.of());
    List<PostingResult> results =
        process(proc, new RuntimeException("alone"), Severity.ERROR, Map.of());
    assertNotNull(results);
    assertTrue(results.isEmpty());
  }

  // -----------------------------------------------------------------------
  // Tests: service isolation — createIssue and commentOnIssue throwing (Gap 2)
  //
  // The existing failingService test only covers findDuplicate() throwing.
  // These tests verify the same isolation guarantee when the exception occurs
  // inside createIssue() or commentOnIssue(), which are reached only after
  // findDuplicate() has returned successfully.
  // -----------------------------------------------------------------------

  @Test
  void createIssueThrowing_doesNotPreventOtherServicesFromRunning() {
    // First service throws from createIssue (findDuplicate returns notFound).
    // Second service is healthy and must still be called.
    CreateIssueThrowingService failingSvc = new CreateIssueThrowingService("create-fail");
    RecordingPostingService goodSvc =
        new RecordingPostingService("good", DuplicateSearchResult.notFound());

    ExceptionProcessor proc = processor(List.of(failingSvc, goodSvc));
    List<PostingResult> results =
        process(proc, new RuntimeException("oops"), Severity.ERROR, Map.of());

    assertEquals(2, results.size(), "Both services must produce a result");

    PostingResult failResult = results.get(0);
    assertFalse(failResult.success(), "createIssue failure must produce an unsuccessful result");
    assertTrue(
        failResult.errorMessage().contains("create-fail"),
        "Error message must reference the failing service name");

    PostingResult goodResult = results.get(1);
    assertTrue(
        goodResult.success(), "Good service must still succeed after peer throws in createIssue");
    assertEquals(
        1, goodSvc.createIssueCalls.size(), "Good service's createIssue must still be called");
  }

  @Test
  void createIssueThrowing_failureResultContainsServiceName() {
    CreateIssueThrowingService failingSvc = new CreateIssueThrowingService("create-fail");

    ExceptionProcessor proc = processor(List.of(failingSvc));
    List<PostingResult> results =
        process(proc, new RuntimeException("oops"), Severity.ERROR, Map.of());

    assertEquals(1, results.size());
    assertFalse(results.get(0).success());
    assertTrue(
        results.get(0).errorMessage().contains("create-fail"),
        "Failure message must include the service name for diagnostics");
  }

  @Test
  void commentOnIssueThrowing_doesNotPreventOtherServicesFromRunning() {
    // First service has a duplicate, so commentOnIssue is called — and throws.
    // Second service is healthy and must still be called.
    CommentOnIssueThrowingService failingSvc = new CommentOnIssueThrowingService("comment-fail");
    RecordingPostingService goodSvc =
        new RecordingPostingService("good", DuplicateSearchResult.notFound());

    ExceptionProcessor proc = processor(List.of(failingSvc, goodSvc));
    List<PostingResult> results =
        process(proc, new RuntimeException("oops"), Severity.ERROR, Map.of());

    assertEquals(2, results.size(), "Both services must produce a result");

    PostingResult failResult = results.get(0);
    assertFalse(failResult.success(), "commentOnIssue failure must produce an unsuccessful result");
    assertTrue(
        failResult.errorMessage().contains("comment-fail"),
        "Error message must reference the failing service name");

    PostingResult goodResult = results.get(1);
    assertTrue(
        goodResult.success(),
        "Good service must still succeed after peer throws in commentOnIssue");
    assertEquals(
        1, goodSvc.createIssueCalls.size(), "Good service's createIssue must still be called");
  }

  @Test
  void commentOnIssueThrowing_failureResultContainsServiceName() {
    CommentOnIssueThrowingService failingSvc = new CommentOnIssueThrowingService("comment-fail");

    ExceptionProcessor proc = processor(List.of(failingSvc));
    List<PostingResult> results =
        process(proc, new RuntimeException("oops"), Severity.ERROR, Map.of());

    assertEquals(1, results.size());
    assertFalse(results.get(0).success());
    assertTrue(
        results.get(0).errorMessage().contains("comment-fail"),
        "Failure message must include the service name for diagnostics");
  }
}
