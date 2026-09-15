package com.pse.auth.mail;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Puts an HTML body in somebody's inbox, from this application's address, with the logo
 * inlined. It knows nothing about what the mail says.
 *
 * <p>Separated from the two classes that compose the mails so that "how a message is
 * transported" stops being restated next to "what the message contains" -- the old
 * {@code MailService} held both, plus the location lookup that one of the bodies needed.
 *
 * <p>Not called {@code MailSender}: that bean name is Spring Boot's own, for the
 * {@code JavaMailSender} this class delegates to, and taking it stops the context starting.
 */
@Component
public class HtmlMailSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(HtmlMailSender.class);

    private static final String LOGO_CONTENT_ID = "rateMyProfLogo";

    private static final String LOGO_PATH = "static/mail/logo.png";

    private final JavaMailSender mailSender;
    private final String senderMail;
    private final String senderName;

    /**
     * Creates HtmlMailSender.
     *
     * @param mailSender the mailSender
     * @param senderMail the senderMail
     * @param senderName the senderName
     */
    public HtmlMailSender(
            JavaMailSender mailSender,
            @Value("${app.mail.from-address}") String senderMail,
            @Value("${app.mail.from-name:RateMyProfApp}") String senderName
    ) {
        this.mailSender = mailSender;
        this.senderMail = senderMail;
        this.senderName = senderName;
    }

    /**
     * Executes send.
     *
     * @param to the to
     * @param subject the subject
     * @param htmlBody the htmlBody
     *
     * @throws IllegalStateException if the operation fails
     */
    public void send(String to, String subject, String htmlBody) {

        MimeMessage message = mailSender.createMimeMessage();

        try {
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setFrom(senderMail, senderName);

            //true means that the email body contains HTML.
            helper.setText(htmlBody, true);

            addLogo(helper);

            mailSender.send(message);

        } catch (MessagingException | UnsupportedEncodingException exception) {
            throw new IllegalStateException("Could not send email to: " + to, exception);
        }
    }

    /**
     * Adds the RateMyProfApp logo as an inline email image.
     */
    private void addLogo(MimeMessageHelper helper) throws MessagingException {

        ClassPathResource logo = new ClassPathResource(LOGO_PATH);

        if (!logo.exists()) {
            LOGGER.warn("Email logo could not be found: {}", logo.getPath());

            return;
        }

        helper.addInline(LOGO_CONTENT_ID, logo, "image/png");
    }
}
