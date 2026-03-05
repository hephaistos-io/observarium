package io.hephaistos.observarium.scrub;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DefaultDataScrubberTest {

  private static final String REDACTED = "[REDACTED]";

  // -----------------------------------------------------------------------
  // NONE level
  // -----------------------------------------------------------------------

  @Test
  void noneLevel_returnsTextUnchanged() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.NONE);
    String sensitive = "password=supersecret token=abc123 user@example.com";
    assertEquals(sensitive, scrubber.scrub(sensitive));
  }

  @Test
  void noneLevel_nullInputReturnsNull() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.NONE);
    assertNull(scrubber.scrub(null));
  }

  // -----------------------------------------------------------------------
  // Null input (any non-NONE level)
  // -----------------------------------------------------------------------

  @Test
  void basicLevel_nullInputReturnsNull() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    assertNull(scrubber.scrub(null));
  }

  @Test
  void strictLevel_nullInputReturnsNull() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    assertNull(scrubber.scrub(null));
  }

  // -----------------------------------------------------------------------
  // BASIC level — credential-style patterns
  // -----------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(
      strings = {
        "password=hunter2",
        "passwd=hunter2",
        "pwd=hunter2",
        "secret=topsecret",
        "token=abc123xyz",
        "api_key=sk-1234567890",
        "apikey=sk-1234567890",
        "authorization=Basic dXNlcjpwYXNz",
        "auth_token=tok-abc",
        "access_token=at-xyz",
        "private_key=BEGIN_RSA"
      })
  void basicLevel_redactsCredentialKeyValuePairs(String input) {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    String result = scrubber.scrub(input);
    assertTrue(result.contains(REDACTED), "Expected BASIC scrubber to redact: " + input);
  }

  @Test
  void basicLevel_redactsBearerToken() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    // Use a header name ("X-Auth") that is NOT in the keyword list so the credential
    // key=value pattern does not fire first and consume the "Bearer" keyword.
    String input = "X-Auth: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.sig";
    String result = scrubber.scrub(input);
    assertTrue(result.contains(REDACTED), "Bearer token must be redacted");
    assertFalse(
        result.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"),
        "Raw Bearer token value must not appear in output");
  }

  @Test
  void basicLevel_caseInsensitiveKeyMatching() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    // Keys in upper-case and mixed-case should still be redacted.
    assertTrue(scrubber.scrub("PASSWORD=secret").contains(REDACTED));
    assertTrue(scrubber.scrub("Token=abc").contains(REDACTED));
  }

  @Test
  void basicLevel_doesNotRedactPlainText() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    String plain = "User performed action on resource";
    assertEquals(plain, scrubber.scrub(plain));
  }

  @Test
  void basicLevel_doesNotRedactEmails() {
    // Emails are a STRICT-level concern; BASIC must leave them intact.
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    String input = "Contact user@example.com for details";
    String result = scrubber.scrub(input);
    assertTrue(result.contains("user@example.com"), "BASIC level must not redact email addresses");
  }

  @Test
  void basicLevel_doesNotRedactIpAddresses() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    String input = "Request from 192.168.1.100";
    String result = scrubber.scrub(input);
    assertTrue(result.contains("192.168.1.100"), "BASIC level must not redact IP addresses");
  }

  // -----------------------------------------------------------------------
  // STRICT level — emails, IPs, phone numbers
  // -----------------------------------------------------------------------

  @Test
  void strictLevel_redactsEmail() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    String result = scrubber.scrub("Contact user@example.com for support");
    assertTrue(result.contains(REDACTED), "Email address must be redacted at STRICT level");
    assertFalse(result.contains("user@example.com"));
  }

  @Test
  void strictLevel_redactsIpAddress() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    String result = scrubber.scrub("Request from 10.0.0.1 failed");
    assertTrue(result.contains(REDACTED), "IP address must be redacted at STRICT level");
    assertFalse(result.contains("10.0.0.1"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Call 555-867-5309", "Call 555.867.5309", "Call 5558675309"})
  void strictLevel_redactsPhoneNumbers(String input) {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    String result = scrubber.scrub(input);
    assertTrue(
        result.contains(REDACTED), "Phone number must be redacted at STRICT level: " + input);
  }

  @Test
  void strictLevel_alsoRedactsBasicPatterns() {
    // STRICT is a superset of BASIC.
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    String input = "token=abc123 and user@example.com";
    String result = scrubber.scrub(input);
    assertTrue(result.contains(REDACTED), "STRICT must redact BASIC patterns too");
    assertFalse(result.contains("abc123"), "Token value must be gone");
    assertFalse(result.contains("user@example.com"), "Email must be gone");
  }

  // -----------------------------------------------------------------------
  // Custom additional patterns
  // -----------------------------------------------------------------------

  @Test
  void customPattern_isApplied_whenLevelIsBasic() {
    // Custom patterns fire for any level other than NONE (NONE exits early before
    // applying additional patterns as well).
    Pattern ssn = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC, List.of(ssn));

    // Input does not match any built-in BASIC patterns, so only the custom one fires.
    String input = "SSN: 123-45-6789";
    String result = scrubber.scrub(input);
    assertTrue(result.contains(REDACTED), "Custom pattern must redact when level is BASIC");
    assertFalse(result.contains("123-45-6789"));
  }

  @Test
  void noneLevel_doesNotApplyCustomPatterns() {
    // The implementation exits early for NONE; custom patterns are not executed.
    Pattern ssn = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.NONE, List.of(ssn));

    String input = "SSN: 123-45-6789";
    String result = scrubber.scrub(input);
    assertEquals(
        input,
        result,
        "NONE level must return the original text unchanged, including bypassing custom patterns");
  }

  @Test
  void customPattern_combinedWithBasicLevel() {
    Pattern orderId = Pattern.compile("ORDER-\\d+");
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC, List.of(orderId));

    String input = "token=xyz ORDER-99999 some text";
    String result = scrubber.scrub(input);

    assertTrue(result.contains(REDACTED));
    assertFalse(result.contains("xyz"), "Token value must be redacted");
    assertFalse(result.contains("ORDER-99999"), "Custom pattern must be redacted");
  }

  @Test
  void multipleCustomPatterns_allApplied() {
    Pattern p1 = Pattern.compile("CUST-\\d+");
    Pattern p2 = Pattern.compile("INV-\\d+");
    // Use BASIC (not NONE) so the scrubber does not short-circuit before custom patterns.
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC, List.of(p1, p2));

    // Input does not match any built-in BASIC patterns.
    String input = "Customer CUST-1001 has invoice INV-2002";
    String result = scrubber.scrub(input);

    assertFalse(result.contains("CUST-1001"));
    assertFalse(result.contains("INV-2002"));
    assertEquals(
        2,
        countOccurrences(result, REDACTED),
        "Both custom patterns must each produce a [REDACTED] token");
  }

  // -----------------------------------------------------------------------
  // helpers
  // -----------------------------------------------------------------------

  private static int countOccurrences(String text, String token) {
    int count = 0;
    int idx = 0;
    while ((idx = text.indexOf(token, idx)) != -1) {
      count++;
      idx += token.length();
    }
    return count;
  }
}
