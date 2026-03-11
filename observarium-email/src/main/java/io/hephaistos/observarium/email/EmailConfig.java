package io.hephaistos.observarium.email;

/**
 * Configuration for the email posting service.
 *
 * @param smtpHost SMTP server hostname
 * @param smtpPort SMTP server port (default 587)
 * @param from Sender email address
 * @param to Recipient email address(es), comma-separated for multiple
 * @param username SMTP authentication username
 * @param password SMTP authentication password
 * @param startTls Whether to enable STARTTLS (default true)
 */
public record EmailConfig(
    String smtpHost,
    int smtpPort,
    String from,
    String to,
    String username,
    String password,
    boolean startTls) {

  /** Compact canonical constructor that validates required fields. */
  public EmailConfig {
    if (smtpHost == null || smtpHost.isBlank()) {
      throw new IllegalArgumentException("EmailConfig.smtpHost must not be blank");
    }
    if (from == null || from.isBlank()) {
      throw new IllegalArgumentException("EmailConfig.from must not be blank");
    }
    if (to == null || to.isBlank()) {
      throw new IllegalArgumentException("EmailConfig.to must not be blank");
    }
  }

  public EmailConfig(String smtpHost, String from, String to, String username, String password) {
    this(smtpHost, 587, from, to, username, password, true);
  }
}
