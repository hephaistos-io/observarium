package io.hephaistos.observarium.posting;

import static org.junit.jupiter.api.Assertions.*;

import io.hephaistos.observarium.event.ExceptionEvent;
import org.junit.jupiter.api.Test;

/**
 * Tests for default methods on {@link PostingService}.
 *
 * <p>Uses a minimal anonymous implementation since only the default methods are under test.
 */
class PostingServiceTest {

  private final PostingService service =
      new PostingService() {
        @Override
        public String name() {
          return "test";
        }

        @Override
        public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
          return DuplicateSearchResult.notFound();
        }

        @Override
        public PostingResult createIssue(ExceptionEvent event) {
          return PostingResult.failure("stub");
        }

        @Override
        public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
          return PostingResult.failure("stub");
        }
      };

  @Test
  void fingerprintLabel_usesFirst12Chars() {
    assertEquals("observarium-abc123def456", service.fingerprintLabel("abc123def456xyz789"));
  }

  @Test
  void fingerprintLabel_usesFullFingerprint_whenShorterThan12() {
    assertEquals("observarium-short", service.fingerprintLabel("short"));
  }

  @Test
  void fingerprintLabel_usesFullFingerprint_whenExactly12Chars() {
    String fp = "exactly12chr";
    assertEquals(12, fp.length(), "precondition");
    assertEquals("observarium-exactly12chr", service.fingerprintLabel(fp));
  }

  @Test
  void fingerprintLabel_alwaysStartsWithPrefix() {
    assertTrue(service.fingerprintLabel("anyfingerprint").startsWith("observarium-"));
  }

  @Test
  void fingerprintLabel_throwsOnNullFingerprint() {
    assertThrows(NullPointerException.class, () -> service.fingerprintLabel(null));
  }

  @Test
  void postCommentLimitNotice_defaultReturnsFailure() {
    PostingResult result = service.postCommentLimitNotice("ISSUE-1", 5);
    assertFalse(result.success(), "Default postCommentLimitNotice must return a failure result");
    assertTrue(
        result.errorMessage().contains("test"), "Failure message must reference the service name");
  }

  // -----------------------------------------------------------------------
  // DuplicateSearchResult factory methods
  // -----------------------------------------------------------------------

  @Test
  void notFound_returnsCommentCountUnknown() {
    DuplicateSearchResult result = DuplicateSearchResult.notFound();
    assertFalse(result.found());
    assertEquals(DuplicateSearchResult.COMMENT_COUNT_UNKNOWN, result.commentCount());
  }

  @Test
  void found_twoArg_returnsCommentCountUnknown() {
    DuplicateSearchResult result = DuplicateSearchResult.found("ID-1", "https://t/1");
    assertTrue(result.found());
    assertEquals(DuplicateSearchResult.COMMENT_COUNT_UNKNOWN, result.commentCount());
  }

  @Test
  void found_threeArg_storesCommentCount() {
    DuplicateSearchResult result = DuplicateSearchResult.found("ID-1", "https://t/1", 7);
    assertEquals(7, result.commentCount());
  }

  @Test
  void found_threeArg_acceptsZero() {
    DuplicateSearchResult result = DuplicateSearchResult.found("ID-1", "https://t/1", 0);
    assertEquals(0, result.commentCount());
  }

  @Test
  void found_threeArg_acceptsMinusOne() {
    DuplicateSearchResult result =
        DuplicateSearchResult.found(
            "ID-1", "https://t/1", DuplicateSearchResult.COMMENT_COUNT_UNKNOWN);
    assertEquals(DuplicateSearchResult.COMMENT_COUNT_UNKNOWN, result.commentCount());
  }

  @Test
  void found_threeArg_rejectsNegativeBelowMinusOne() {
    assertThrows(
        IllegalArgumentException.class,
        () -> DuplicateSearchResult.found("ID-1", "https://t/1", -2));
  }
}
