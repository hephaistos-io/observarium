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
  // Empty string input
  // -----------------------------------------------------------------------

  @Test
  void basicLevel_emptyStringReturnsEmptyString() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    assertEquals("", scrubber.scrub(""));
  }

  @Test
  void strictLevel_emptyStringReturnsEmptyString() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    assertEquals("", scrubber.scrub(""));
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
        "auth_token=tok-abc",
        "access_token=at-xyz",
        "private_key=BEGIN_RSA"
      })
  void basicLevel_redactsCredentialKeyValuePairs(String input) {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    // The entire key=value expression is replaced with [REDACTED].
    assertEquals(REDACTED, scrubber.scrub(input));
  }

  @Test
  void basicLevel_redactsCredentialKeyValuePair_authorizationWithSpaceInValue() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    // The credential value pattern (\S{1,200}) stops at whitespace, so "Basic" is the
    // matched value token and the remainder "dXNlcjpwYXNz" is left as-is in the output.
    String input = "authorization=Basic dXNlcjpwYXNz";
    String result = scrubber.scrub(input);
    assertTrue(result.contains(REDACTED), "authorization keyword must be redacted");
    assertFalse(result.contains("authorization="), "Raw key=value must not remain");
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
    assertEquals(REDACTED, scrubber.scrub("PASSWORD=secret"));
    assertEquals(REDACTED, scrubber.scrub("Token=abc"));
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
  // BASIC level — colon separator
  // -----------------------------------------------------------------------

  @Test
  void basicLevel_redactsCredential_withColonSeparator() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    // The regex supports both "=" and ":" as separators.
    assertEquals(REDACTED, scrubber.scrub("password: hunter2"));
  }

  @Test
  void basicLevel_redactsCredential_withColonAndSpaceAroundSeparator() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    // Spaces are allowed on both sides of the separator via \s*[:=]\s*.
    assertEquals(REDACTED, scrubber.scrub("token : abc123"));
  }

  @Test
  void basicLevel_redactsCredential_withColonSeparatorEmbeddedInSentence() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    // The value pattern (\S{1,200}) is greedy and non-whitespace, so it includes the
    // trailing comma: "topsecret," becomes part of the match.  The replacement therefore
    // consumes the comma, producing "retry" with a leading space rather than ", retry".
    String input = "Auth failed, secret: topsecret, retry";
    String result = scrubber.scrub(input);
    assertEquals("Auth failed, [REDACTED] retry", result);
    assertFalse(result.contains("topsecret"));
  }

  // -----------------------------------------------------------------------
  // BASIC level — quoted values
  // -----------------------------------------------------------------------

  @Test
  void basicLevel_redactsCredential_withDoubleQuotedValue() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    // Quotes are non-whitespace so \S{1,200} matches them as part of the value.
    assertEquals(REDACTED, scrubber.scrub("password=\"hunter2\""));
  }

  @Test
  void basicLevel_redactsCredential_withSingleQuotedValue() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    assertEquals(REDACTED, scrubber.scrub("token='abc123xyz'"));
  }

  @Test
  void basicLevel_redactsCredential_withQuotedValueEmbeddedInSentence() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    String input = "Login failed: password=\"s3cr3t\" at endpoint";
    String result = scrubber.scrub(input);
    assertEquals("Login failed: [REDACTED] at endpoint", result);
    assertFalse(result.contains("s3cr3t"));
  }

  // -----------------------------------------------------------------------
  // BASIC level — partial keyword matches (non-sensitive keys)
  // -----------------------------------------------------------------------

  @Test
  void basicLevel_doesNotRedactPartialKeywordMatch_passwordless() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    // "passwordless" is not in the keyword list; only "password" is, and it must be
    // followed immediately by [:=].  "passwordless=foo" has "less" between "password"
    // and "=", so the keyword-value pattern must not match.
    String input = "passwordless=foo";
    assertEquals(input, scrubber.scrub(input));
  }

  @ParameterizedTest
  @ValueSource(strings = {"tokens=many", "secrets_manager=aws", "my_passwords=file.txt"})
  void basicLevel_doesNotRedactKeywordAsPrefixOfLongerWord(String input) {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    // The keyword regex uses alternation (password|token|secret|...) which matches
    // substrings. "tokens" starts with "token" and is followed by "s=", so the regex
    // sees "token" + separator "s" — but "s" is not [:=]. Verify actual behavior.
    String result = scrubber.scrub(input);
    assertEquals(input, result);
  }

  // -----------------------------------------------------------------------
  // BASIC level — multiple credentials on one line
  // -----------------------------------------------------------------------

  @Test
  void basicLevel_redactsMultipleCredentialsOnSameLine() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    String input = "password=abc token=def secret=ghi";
    String result = scrubber.scrub(input);
    assertFalse(result.contains("abc"), "First credential value must be redacted");
    assertFalse(result.contains("def"), "Second credential value must be redacted");
    assertFalse(result.contains("ghi"), "Third credential value must be redacted");
    assertEquals(3, countOccurrences(result, REDACTED), "Each credential must produce a marker");
  }

  // -----------------------------------------------------------------------
  // BASIC level — special characters in values
  // -----------------------------------------------------------------------

  @Test
  void basicLevel_redactsCredentialWithSpecialCharactersInValue() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    // Non-whitespace special chars are part of \S{1,200}
    String input = "password=p@ss!w0rd#123";
    assertEquals(REDACTED, scrubber.scrub(input));
  }

  @Test
  void basicLevel_redactsCredentialWithBase64Value() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    String input = "token=dGVzdDp0ZXN0+abc/def==";
    assertEquals(REDACTED, scrubber.scrub(input));
  }

  // -----------------------------------------------------------------------
  // BASIC level — value length cap (200 chars)
  // -----------------------------------------------------------------------

  @Test
  void basicLevel_valueLengthCappedAt200Characters() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    // The credential value regex is \S{1,200}, so a value longer than 200 non-whitespace
    // chars should only match the first 200 characters, leaving the remainder in the output.
    String longValue = "x".repeat(250);
    String input = "token=" + longValue;
    String result = scrubber.scrub(input);
    assertTrue(result.startsWith(REDACTED), "First 200 chars of value must be redacted");
    // The remaining 50 chars should survive
    assertEquals(REDACTED + "x".repeat(50), result);
  }

  // -----------------------------------------------------------------------
  // BASIC level — authorization keyword standalone
  // -----------------------------------------------------------------------

  @Test
  void basicLevel_redactsAuthorizationWithEqualsSign() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    assertEquals(REDACTED, scrubber.scrub("authorization=Bearer_xyz"));
  }

  @Test
  void basicLevel_redactsAuthorizationWithColonSeparator() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    String result = scrubber.scrub("Authorization: Basic dXNlcjpwYXNz");
    assertTrue(result.contains(REDACTED));
    assertFalse(result.contains("Authorization:"), "Key must be consumed by the match");
  }

  // -----------------------------------------------------------------------
  // BASIC level — Bearer token with base64 padding
  // -----------------------------------------------------------------------

  @Test
  void basicLevel_redactsBearerTokenWithBase64Padding() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    // The Bearer pattern includes =* for base64 padding
    String input = "X-Auth: Bearer dGVzdA==";
    String result = scrubber.scrub(input);
    assertFalse(result.contains("dGVzdA=="), "Bearer token with padding must be redacted");
    assertTrue(result.contains(REDACTED));
  }

  // -----------------------------------------------------------------------
  // Multi-line / stack trace scrubbing
  // -----------------------------------------------------------------------

  @Test
  void basicLevel_redactsCredentialEmbeddedInMultiLineStackTrace() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.BASIC);
    // Realistic stack trace where a credential leaks through an exception message.
    String input =
        "java.sql.SQLException: Login failed for user 'admin' with password=hunter2\n"
            + "\tat com.example.db.DataSource.connect(DataSource.java:42)\n"
            + "\tat com.example.service.UserService.login(UserService.java:87)\n"
            + "\tat com.example.api.AuthController.authenticate(AuthController.java:55)\n"
            + "Caused by: java.io.IOException: token=secret-refresh-xyz\n"
            + "\tat com.example.auth.TokenStore.refresh(TokenStore.java:19)";

    String result = scrubber.scrub(input);

    assertFalse(result.contains("hunter2"), "Password value must be redacted in stack trace");
    assertFalse(
        result.contains("secret-refresh-xyz"), "Token value must be redacted in stack trace");
    assertTrue(result.contains(REDACTED), "Redacted marker must be present");
    // Stack frame lines must be preserved intact.
    assertTrue(result.contains("\tat com.example.db.DataSource.connect(DataSource.java:42)"));
    assertTrue(result.contains("\tat com.example.service.UserService.login(UserService.java:87)"));
  }

  @Test
  void strictLevel_redactsAllSensitiveDataInMultiLineStackTrace() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    String input =
        "com.example.RemoteCallException: Remote call from 10.0.0.42 failed\n"
            + "\tat com.example.Client.call(Client.java:33)\n"
            + "Caused by: java.lang.RuntimeException: auth failed for user@example.com\n"
            + "\tat com.example.Auth.verify(Auth.java:11)\n"
            + "Context: api_key=sk-12345 ipv6=2001:db8::1";

    String result = scrubber.scrub(input);

    assertFalse(result.contains("10.0.0.42"), "IPv4 must be redacted at STRICT level");
    assertFalse(result.contains("user@example.com"), "Email must be redacted at STRICT level");
    assertFalse(result.contains("sk-12345"), "API key value must be redacted at STRICT level");
    assertFalse(result.contains("2001:db8::1"), "IPv6 must be redacted at STRICT level");
    assertTrue(result.contains(REDACTED));
    assertTrue(
        result.contains("\tat com.example.Client.call(Client.java:33)"),
        "Stack frame lines must be preserved");
  }

  // -----------------------------------------------------------------------
  // STRICT level — emails, IPs, phone numbers
  // -----------------------------------------------------------------------

  @Test
  void strictLevel_redactsEmail() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    String result = scrubber.scrub("Contact user@example.com for support");
    assertEquals("Contact [REDACTED] for support", result);
  }

  @Test
  void strictLevel_redactsIpv4Address() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    String result = scrubber.scrub("Request from 10.0.0.1 failed");
    assertEquals("Request from [REDACTED] failed", result);
  }

  // -----------------------------------------------------------------------
  // STRICT level — IPv6 addresses
  // -----------------------------------------------------------------------

  @Test
  void strictLevel_redactsIpv6_fullForm() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    String result = scrubber.scrub("peer 2001:0db8:85a3:0000:0000:8a2e:0370:7334 connected");
    assertFalse(
        result.contains("2001:0db8:85a3:0000:0000:8a2e:0370:7334"),
        "Full-form IPv6 must be redacted");
    assertTrue(result.contains(REDACTED));
  }

  @Test
  void strictLevel_redactsIpv6_loopback() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    String result = scrubber.scrub("connection from ::1 rejected");
    assertFalse(result.contains("::1"), "IPv6 loopback ::1 must be redacted");
    assertTrue(result.contains(REDACTED));
  }

  @Test
  void strictLevel_redactsIpv6_compressed() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    String result = scrubber.scrub("host fe80::1 is unreachable");
    assertFalse(result.contains("fe80::1"), "Compressed IPv6 must be redacted");
    assertTrue(result.contains(REDACTED));
  }

  @Test
  void strictLevel_redactsIpv6_fullCompressed() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    String result = scrubber.scrub("route via 2001:db8::1 dropped");
    assertFalse(result.contains("2001:db8::1"), "Compressed IPv6 with :: must be redacted");
    assertTrue(result.contains(REDACTED));
  }

  @Test
  void strictLevel_redactsIpv6_ipv4Mapped() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    // IPv4-mapped IPv6 (::ffff:192.168.1.1): the IPv4 pattern fires first and
    // redacts the 192.168.1.1 portion. The ::ffff: prefix remains in the output
    // because the IPv6 regex cannot match it as a unit (the trailing dotted-decimal
    // part breaks the lookahead). This is an accepted limitation — the actual IP
    // address is scrubbed, and the residual prefix is not sensitive on its own.
    String input = "mapped address ::ffff:192.168.1.1 in log";
    String result = scrubber.scrub(input);
    assertFalse(result.contains("192.168.1.1"), "IPv4 portion must be redacted");
    assertTrue(result.contains(REDACTED));
  }

  @Test
  void strictLevel_doesNotRedactMacAddresses() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    String input = "NIC 00:11:22:33:44:55 is down";
    assertTrue(
        scrubber.scrub(input).contains("00:11:22:33:44:55"),
        "MAC addresses must not be redacted as IPv6");
  }

  @Test
  void strictLevel_doesNotRedactTimeStrings() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    String input = "Event at 12:34:56 logged";
    assertTrue(
        scrubber.scrub(input).contains("12:34:56"),
        "HH:MM:SS time strings must not be redacted as IPv6");
  }

  @Test
  void strictLevel_doesNotRedactNumericRatios() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    String input = "Aspect ratio 16:9:1 used";
    assertTrue(
        scrubber.scrub(input).contains("16:9:1"), "Numeric ratios must not be redacted as IPv6");
  }

  @Test
  void strictLevel_redactsHexScopeResolutionAsIpv6() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    // abc::def is a valid compressed IPv6 address. The scrubber intentionally matches
    // it even though it could also be a C++/Rust scope-resolution expression.
    // Over-redaction is preferred at STRICT level over potential address leaks.
    String result = scrubber.scrub("called abc::def in module");
    assertFalse(
        result.contains("abc::def"), "Hex scope tokens are treated as IPv6 at STRICT level");
    assertTrue(result.contains(REDACTED));
  }

  @ParameterizedTest
  @ValueSource(strings = {"Call 555-867-5309", "Call 555.867.5309", "Call 5558675309"})
  void strictLevel_redactsPhoneNumbers(String input) {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    String result = scrubber.scrub(input);
    assertTrue(
        result.contains(REDACTED), "Phone number must be redacted at STRICT level: " + input);
  }

  // -----------------------------------------------------------------------
  // STRICT level — false positive boundaries
  // -----------------------------------------------------------------------

  @Test
  void strictLevel_redactsIpv4_invalidOctets() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    // 999.999.999.999 is not a valid IP but matches \d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}.
    // The scrubber intentionally over-matches — it's a regex heuristic, not an IP parser.
    String result = scrubber.scrub("address 999.999.999.999 logged");
    assertTrue(result.contains(REDACTED), "Scrubber matches dotted-quad pattern even if invalid");
  }

  @Test
  void strictLevel_doesNotRedactVersionStringsAsIpv4() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    // Version "1.2.3" has only three segments — the IPv4 regex requires exactly four.
    String input = "Using library version 1.2.3 in production";
    assertEquals(input, scrubber.scrub(input));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "user+tag@example.com",
        "first.last@company.co.uk",
        "a@b.co",
        "ALL_CAPS@DOMAIN.ORG"
      })
  void strictLevel_redactsVariousEmailFormats(String email) {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    String result = scrubber.scrub("contact " + email + " for info");
    assertFalse(result.contains(email), "Email format '" + email + "' must be redacted");
    assertTrue(result.contains(REDACTED));
  }

  @Test
  void strictLevel_doesNotRedactPhoneNumber_tooFewDigits() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    // 9 digits — below the 10-digit phone pattern
    String input = "Reference 123-456-789 is not a phone number";
    assertEquals(input, scrubber.scrub(input));
  }

  @Test
  void strictLevel_redactsMultipleIpAddressesOnSameLine() {
    DefaultDataScrubber scrubber = new DefaultDataScrubber(ScrubLevel.STRICT);
    String input = "Traffic from 10.0.0.1 to 192.168.1.1 blocked";
    String result = scrubber.scrub(input);
    assertFalse(result.contains("10.0.0.1"));
    assertFalse(result.contains("192.168.1.1"));
    assertEquals(2, countOccurrences(result, REDACTED));
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
    assertEquals("SSN: [REDACTED]", result);
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
