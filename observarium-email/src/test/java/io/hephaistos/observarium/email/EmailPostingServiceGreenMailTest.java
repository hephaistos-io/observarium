package io.hephaistos.observarium.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.event.Severity;
import io.hephaistos.observarium.posting.PostingResult;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Integration tests for {@link EmailPostingService} that exercise successful SMTP delivery using
 * GreenMail as an embedded mail server. These tests complement the unit tests in {@link
 * EmailPostingServiceTest}, which cover failure paths, configuration, and stateless operations.
 *
 * <p>{@link EmailPostingService#buildSession()} sets {@code mail.smtp.auth=true} and attaches an
 * {@link jakarta.mail.Authenticator} only when {@link EmailConfig#auth()} is {@code true}. For
 * tests that are not specifically about authentication, a shared test user ({@code DEFAULT_USER} /
 * {@code DEFAULT_PASS}) is registered with GreenMail before each test.
 */
class EmailPostingServiceGreenMailTest {

  @RegisterExtension
  static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

  private static final String DEFAULT_USER = "sender";
  private static final String DEFAULT_PASS = "secret";
  private static final String DEFAULT_FROM = "from@example.com";
  private static final String DEFAULT_TO = "to@example.com";

  /**
   * Registers the shared test user before each test so that GreenMail accepts AUTH credentials for
   * all no-auth-focused tests. GreenMail resets its mailbox state between tests, so registration
   * must happen in {@code @BeforeEach} rather than once in a static initializer.
   */
  @BeforeEach
  void registerDefaultUser() {
    greenMail.setUser(DEFAULT_FROM, DEFAULT_USER, DEFAULT_PASS);
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  /**
   * Builds an {@link EmailConfig} pointed at the GreenMail SMTP port using the shared test
   * credentials and no STARTTLS.
   */
  private EmailConfig defaultConfig(String to) {
    return new EmailConfig(
        "127.0.0.1",
        greenMail.getSmtp().getPort(),
        DEFAULT_FROM,
        to,
        DEFAULT_USER,
        DEFAULT_PASS,
        true,
        false);
  }

  private EmailConfig defaultConfig() {
    return defaultConfig(DEFAULT_TO);
  }

  /**
   * Builds a minimal {@link ExceptionEvent} with a fixed fingerprint. All optional fields (traceId,
   * spanId, tags) are absent unless overridden by a specific test.
   */
  private static ExceptionEvent minimalEvent(String fingerprint) {
    return ExceptionEvent.builder()
        .fingerprint(fingerprint)
        .exceptionClass("com.example.TestException")
        .message("test failure")
        .rawStackTrace(
            "com.example.TestException: test failure\n\tat com.example.App.run(App.java:20)")
        .severity(Severity.ERROR)
        .timestamp(Instant.parse("2025-06-01T10:00:00Z"))
        .threadName("test-thread")
        .build();
  }

  // -----------------------------------------------------------------------
  // 1. Successful delivery
  // -----------------------------------------------------------------------

  @Test
  void createIssue_sendsEmail_successfully() {
    EmailPostingService service = new EmailPostingService(defaultConfig());

    PostingResult result = service.createIssue(minimalEvent("fp-success"));

    assertTrue(result.success(), "createIssue must return success when SMTP delivery succeeds");
    assertNull(result.errorMessage(), "Successful result must carry no error message");
    assertNull(result.externalIssueId(), "Email posting produces no external issue ID");
    assertNull(result.url(), "Email posting produces no URL");
    assertEquals(1, greenMail.getReceivedMessages().length, "Exactly one email must be delivered");
  }

  // -----------------------------------------------------------------------
  // 2. Recipient address
  // -----------------------------------------------------------------------

  @Test
  void createIssue_emailHasCorrectRecipient() throws Exception {
    EmailPostingService service = new EmailPostingService(defaultConfig("alerts@example.com"));

    service.createIssue(minimalEvent("fp-recipient"));

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertEquals(1, received.length);
    assertEquals(
        "alerts@example.com",
        received[0].getAllRecipients()[0].toString(),
        "TO address must match the configured recipient");
  }

  // -----------------------------------------------------------------------
  // 3. Sender address
  // -----------------------------------------------------------------------

  @Test
  void createIssue_emailHasCorrectSender() throws Exception {
    EmailPostingService service = new EmailPostingService(defaultConfig());

    service.createIssue(minimalEvent("fp-sender"));

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertEquals(1, received.length);
    assertEquals(
        DEFAULT_FROM,
        received[0].getFrom()[0].toString(),
        "FROM address must match the configured sender");
  }

  // -----------------------------------------------------------------------
  // 4. Subject line
  // -----------------------------------------------------------------------

  @Test
  void createIssue_emailSubject_containsExceptionInfo() throws Exception {
    ExceptionEvent event =
        ExceptionEvent.builder()
            .fingerprint("fp-subject")
            .exceptionClass("java.lang.NullPointerException")
            .message("something is null")
            .rawStackTrace("java.lang.NullPointerException\n\tat com.example.App.run(App.java:5)")
            .severity(Severity.ERROR)
            .timestamp(Instant.now())
            .threadName("main")
            .build();

    EmailPostingService service = new EmailPostingService(defaultConfig());

    service.createIssue(event);

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertEquals(1, received.length);
    String subject = received[0].getSubject();
    // DefaultIssueFormatter.title() produces "[Observarium] ShortClassName: message"
    assertTrue(
        subject.contains("[Observarium]"),
        "Subject must contain the [Observarium] prefix but was: " + subject);
    assertTrue(
        subject.contains("NullPointerException"),
        "Subject must contain the unqualified exception class name but was: " + subject);
    assertTrue(
        subject.contains("something is null"),
        "Subject must contain the exception message but was: " + subject);
  }

  // -----------------------------------------------------------------------
  // 5. Body — exception class
  // -----------------------------------------------------------------------

  @Test
  void createIssue_emailBody_containsExceptionClass() throws Exception {
    ExceptionEvent event =
        ExceptionEvent.builder()
            .fingerprint("fp-body-class")
            .exceptionClass("com.example.MySpecificException")
            .message("body test")
            .rawStackTrace("com.example.MySpecificException\n\tat App.run(App.java:1)")
            .severity(Severity.WARNING)
            .timestamp(Instant.now())
            .threadName("main")
            .build();

    EmailPostingService service = new EmailPostingService(defaultConfig());

    service.createIssue(event);

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertEquals(1, received.length);
    String body = (String) received[0].getContent();
    assertTrue(
        body.contains("com.example.MySpecificException"),
        "Body must contain the fully-qualified exception class name");
  }

  // -----------------------------------------------------------------------
  // 6. Body — stack trace
  // -----------------------------------------------------------------------

  @Test
  void createIssue_emailBody_containsStackTrace() throws Exception {
    String rawStackTrace =
        "com.example.StackException: stack error\n\tat com.example.Service.call(Service.java:42)";
    ExceptionEvent event =
        ExceptionEvent.builder()
            .fingerprint("fp-body-stack")
            .exceptionClass("com.example.StackException")
            .message("stack error")
            .rawStackTrace(rawStackTrace)
            .severity(Severity.ERROR)
            .timestamp(Instant.now())
            .threadName("worker")
            .build();

    EmailPostingService service = new EmailPostingService(defaultConfig());

    service.createIssue(event);

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertEquals(1, received.length);
    String body = (String) received[0].getContent();
    assertTrue(body.contains("STACK TRACE"), "Body must contain the STACK TRACE section header");
    assertTrue(
        body.contains("com.example.Service.call(Service.java:42)"),
        "Body must contain the raw stack trace frames");
  }

  // -----------------------------------------------------------------------
  // 7. Body — fingerprint
  // -----------------------------------------------------------------------

  @Test
  void createIssue_emailBody_containsFingerprint() throws Exception {
    ExceptionEvent event =
        ExceptionEvent.builder()
            .fingerprint("unique-fp-abc-123")
            .exceptionClass("com.example.FpException")
            .message("fp test")
            .rawStackTrace("com.example.FpException\n\tat App.run(App.java:1)")
            .severity(Severity.ERROR)
            .timestamp(Instant.now())
            .threadName("main")
            .build();

    EmailPostingService service = new EmailPostingService(defaultConfig());

    service.createIssue(event);

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertEquals(1, received.length);
    String body = (String) received[0].getContent();
    assertTrue(
        body.contains("unique-fp-abc-123"),
        "Body must include the event fingerprint for traceability");
  }

  // -----------------------------------------------------------------------
  // 8. Body — tags
  // -----------------------------------------------------------------------

  @Test
  void createIssue_emailBody_containsTags() throws Exception {
    ExceptionEvent event =
        ExceptionEvent.builder()
            .fingerprint("fp-body-tags")
            .exceptionClass("com.example.TaggedException")
            .message("tagged event")
            .rawStackTrace("com.example.TaggedException\n\tat App.run(App.java:1)")
            .severity(Severity.ERROR)
            .timestamp(Instant.now())
            .threadName("main")
            .tags(Map.of("env", "production", "region", "us-east-1"))
            .build();

    EmailPostingService service = new EmailPostingService(defaultConfig());

    service.createIssue(event);

    MimeMessage[] received = greenMail.getReceivedMessages();
    assertEquals(1, received.length);
    String body = (String) received[0].getContent();
    assertTrue(body.contains("TAGS"), "Body must contain the TAGS section header");
    assertTrue(body.contains("env"), "Body must contain the tag key 'env'");
    assertTrue(body.contains("production"), "Body must contain the tag value 'production'");
    assertTrue(body.contains("region"), "Body must contain the tag key 'region'");
    assertTrue(body.contains("us-east-1"), "Body must contain the tag value 'us-east-1'");
  }

  // -----------------------------------------------------------------------
  // 9. Multiple recipients
  // -----------------------------------------------------------------------

  @Test
  void createIssue_sendsToMultipleRecipients() {
    // InternetAddress.parse() splits comma-separated addresses; GreenMail records one
    // MimeMessage per recipient envelope address.
    EmailPostingService service =
        new EmailPostingService(defaultConfig("alpha@example.com,beta@example.com"));

    PostingResult result = service.createIssue(minimalEvent("fp-multi-recipient"));

    assertTrue(result.success(), "createIssue must succeed for multi-recipient config");
    MimeMessage[] received = greenMail.getReceivedMessages();
    assertEquals(
        2,
        received.length,
        "GreenMail must record one delivery per recipient; expected 2 but got " + received.length);
  }

  // -----------------------------------------------------------------------
  // 10. SMTP authentication with explicitly registered credentials
  // -----------------------------------------------------------------------

  @Test
  void createIssue_worksWithAuthentication() {
    // Register a dedicated GreenMail user distinct from the default user, verifying that
    // GreenMail accepts AUTH credentials bound to a specific login/password pair.
    greenMail.setUser("auth-dest@example.com", "authlogin", "authpass");

    EmailConfig config =
        new EmailConfig(
            "127.0.0.1",
            greenMail.getSmtp().getPort(),
            DEFAULT_FROM,
            "auth-dest@example.com",
            DEFAULT_USER,
            DEFAULT_PASS,
            true,
            false);

    EmailPostingService service = new EmailPostingService(config);

    PostingResult result = service.createIssue(minimalEvent("fp-auth"));

    assertTrue(
        result.success(),
        "createIssue must succeed when the server accepts the provided credentials; error was: "
            + result.errorMessage());
    assertEquals(1, greenMail.getReceivedMessages().length);
  }

  // -----------------------------------------------------------------------
  // 11. STARTTLS session configuration
  //
  // Delivering over STARTTLS in an integration test requires the client to
  // complete a TLS handshake against GreenMail's self-signed certificate.
  // Jakarta Mail 2.1+ enforces strict hostname verification, and wiring a
  // custom TrustManager into the service would require modifying production
  // code purely for test purposes. Instead, the test below verifies that
  // buildSession() correctly wires the two JavaMail properties that activate
  // STARTTLS when startTls=true. This is the meaningful regression guard:
  // if the property names are mistyped or the branch is removed, the test
  // fails immediately rather than silently falling back to plain SMTP.
  // -----------------------------------------------------------------------

  @Test
  void buildSession_setsStartTlsProperties_whenStartTlsIsEnabled() {
    EmailConfig config =
        new EmailConfig(
            "127.0.0.1",
            greenMail.getSmtp().getPort(),
            DEFAULT_FROM,
            DEFAULT_TO,
            DEFAULT_USER,
            DEFAULT_PASS,
            true,
            true);

    EmailPostingService service = new EmailPostingService(config);
    Session session = service.buildSession();

    assertEquals(
        "true",
        session.getProperty("mail.smtp.starttls.enable"),
        "mail.smtp.starttls.enable must be \"true\" when startTls=true");
    assertEquals(
        "true",
        session.getProperty("mail.smtp.starttls.required"),
        "mail.smtp.starttls.required must be \"true\" when startTls=true");
  }

  @Test
  void buildSession_doesNotSetStartTlsProperties_whenStartTlsIsDisabled() {
    EmailConfig config =
        new EmailConfig(
            "127.0.0.1",
            greenMail.getSmtp().getPort(),
            DEFAULT_FROM,
            DEFAULT_TO,
            DEFAULT_USER,
            DEFAULT_PASS,
            true,
            false);

    EmailPostingService service = new EmailPostingService(config);
    Session session = service.buildSession();

    assertNull(
        session.getProperty("mail.smtp.starttls.enable"),
        "mail.smtp.starttls.enable must be absent when startTls=false");
    assertNull(
        session.getProperty("mail.smtp.starttls.required"),
        "mail.smtp.starttls.required must be absent when startTls=false");
  }
}
