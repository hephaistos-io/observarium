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
}
