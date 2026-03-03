package io.hephaistos.observarium.email;

/**
 * Configuration for the email posting service.
 *
 * @param smtpHost  SMTP server hostname
 * @param smtpPort  SMTP server port (default 587)
 * @param from      Sender email address
 * @param to        Recipient email address(es), comma-separated for multiple
 * @param username  SMTP authentication username
 * @param password  SMTP authentication password
 * @param startTls  Whether to enable STARTTLS (default true)
 */
public record EmailConfig(
    String smtpHost,
    int smtpPort,
    String from,
    String to,
    String username,
    String password,
    boolean startTls
) {

    public EmailConfig(String smtpHost, String from, String to, String username, String password) {
        this(smtpHost, 587, from, to, username, password, true);
    }
}
