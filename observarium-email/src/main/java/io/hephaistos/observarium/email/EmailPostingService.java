package io.hephaistos.observarium.email;

import static java.util.Objects.requireNonNull;

import io.hephaistos.observarium.event.ExceptionEvent;
import io.hephaistos.observarium.posting.DefaultIssueFormatter;
import io.hephaistos.observarium.posting.DuplicateSearchResult;
import io.hephaistos.observarium.posting.IssueFormatter;
import io.hephaistos.observarium.posting.PostingResult;
import io.hephaistos.observarium.posting.PostingService;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PostingService implementation that delivers exception notifications via SMTP email.
 *
 * <p>Email is inherently fire-and-forget: there is no way to search for an existing "issue" in a
 * recipient's inbox, and there is no thread to comment on. As a result:
 *
 * <ul>
 *   <li>{@link #findDuplicate} always returns {@link DuplicateSearchResult#notFound()}.
 *   <li>{@link #commentOnIssue} always returns a {@link PostingResult#failure} explaining the
 *       limitation.
 * </ul>
 *
 * <p>The email body is formatted as plain text so that it renders readably in any email client
 * without requiring HTML or markdown support.
 */
public class EmailPostingService implements PostingService {

  private static final Logger log = LoggerFactory.getLogger(EmailPostingService.class);

  private static final String SERVICE_NAME = "email";
  private static final String COMMENT_NOT_SUPPORTED =
      "Email posting service does not support commenting on existing issues";

  private final EmailConfig config;
  private final IssueFormatter formatter;
  private final Session session;

  public EmailPostingService(EmailConfig config) {
    this(config, new DefaultIssueFormatter());
  }

  public EmailPostingService(EmailConfig config, IssueFormatter formatter) {
    this.config = requireNonNull(config, "config must not be null");
    this.formatter = requireNonNull(formatter, "formatter must not be null");
    this.session = buildSession();
  }

  @Override
  public String name() {
    return SERVICE_NAME;
  }

  /**
   * Always returns {@link DuplicateSearchResult#notFound()} because email has no mechanism for
   * querying previously sent messages.
   */
  @Override
  public DuplicateSearchResult findDuplicate(ExceptionEvent event) {
    return DuplicateSearchResult.notFound();
  }

  /**
   * Sends an SMTP email describing the exception event.
   *
   * <p>The body is formatted as plain text — not markdown — so that the content is immediately
   * readable in any email client.
   *
   * @return {@link PostingResult#success(String, String) success(null, null)} on delivery; there is
   *     no external issue ID or URL for an email.
   */
  @Override
  public PostingResult createIssue(ExceptionEvent event) {
    try {
      MimeMessage message = new MimeMessage(session);
      message.setFrom(new InternetAddress(config.from()));
      message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(config.to()));
      message.setSubject(formatter.title(event));
      message.setText(buildPlainTextBody(event), "UTF-8");

      Transport.send(message);
      log.info("Exception notification email sent for fingerprint {}", event.fingerprint());
      return PostingResult.success(null, null);

    } catch (Exception e) {
      log.error("Failed to send exception notification email", e);
      return PostingResult.failure("Failed to send email: " + e.getMessage());
    }
  }

  /**
   * Email has no concept of "commenting" on a previously sent notification.
   *
   * @return always a {@link PostingResult#failure} with an explanatory message
   */
  @Override
  public PostingResult commentOnIssue(String externalIssueId, ExceptionEvent event) {
    return PostingResult.failure(COMMENT_NOT_SUPPORTED);
  }

  // --- helpers ---

  Session buildSession() {
    Properties props = new Properties();
    props.put("mail.smtp.host", config.smtpHost());
    props.put("mail.smtp.port", String.valueOf(config.smtpPort()));

    if (config.auth()) {
      props.put("mail.smtp.auth", "true");
    }

    if (config.startTls()) {
      props.put("mail.smtp.starttls.enable", "true");
      props.put("mail.smtp.starttls.required", "true");
    }

    Authenticator authenticator = null;
    if (config.auth()) {
      authenticator =
          new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
              return new PasswordAuthentication(config.username(), config.password());
            }
          };
    }

    return Session.getInstance(props, authenticator);
  }

  /**
   * Produces a plain-text representation of the event suitable for an email body. All fields are
   * written out explicitly so the message is self-contained.
   */
  private String buildPlainTextBody(ExceptionEvent event) {
    var message = event.message() != null ? event.message() : "(no message)";

    var tracing = new StringBuilder();
    if (event.traceId() != null) {
      tracing.append("Trace ID:  %s\n".formatted(event.traceId()));
    }
    if (event.spanId() != null) {
      tracing.append("Span ID:   %s\n".formatted(event.spanId()));
    }

    var tags = "";
    if (!event.tags().isEmpty()) {
      var tagSection = new StringBuilder("\nTAGS\n----\n");
      event.tags().forEach((k, v) -> tagSection.append("%s: %s\n".formatted(k, v)));
      tags = tagSection.toString();
    }

    return """
        EXCEPTION NOTIFICATION
        ======================

        Type:      %s
        Message:   %s
        Severity:  %s
        Timestamp: %s
        Thread:    %s
        %s%s
        STACK TRACE
        -----------
        %s

        Fingerprint: %s
        """
        .formatted(
            event.exceptionClass(),
            message,
            event.severity(),
            event.timestamp(),
            event.threadName(),
            tracing,
            tags,
            event.rawStackTrace(),
            event.fingerprint());
  }
}
