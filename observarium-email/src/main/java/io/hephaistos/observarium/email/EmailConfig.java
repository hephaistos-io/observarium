package io.hephaistos.observarium.email;

/**
 * Configuration for the email posting service.
 *
 * @param smtpHost SMTP server hostname
 * @param smtpPort SMTP server port (default 587)
 * @param from Sender email address
 * @param to Recipient email address(es), comma-separated for multiple
 * @param username SMTP authentication username; required when {@code auth} is {@code true}
 * @param password SMTP authentication password; required when {@code auth} is {@code true}
 * @param auth Whether to enable SMTP authentication (default {@code true})
 * @param startTls Whether to enable STARTTLS (default {@code true})
 */
public record EmailConfig(
    String smtpHost,
    int smtpPort,
    String from,
    String to,
    String username,
    String password,
    boolean auth,
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
    if (auth) {
      if (username == null || username.isBlank()) {
        throw new IllegalArgumentException(
            "EmailConfig.username must not be blank when auth is enabled");
      }
      if (password == null || password.isBlank()) {
        throw new IllegalArgumentException(
            "EmailConfig.password must not be blank when auth is enabled");
      }
    }
  }

  public EmailConfig(String smtpHost, String from, String to, String username, String password) {
    this(smtpHost, 587, from, to, username, password, true, true);
  }
}
