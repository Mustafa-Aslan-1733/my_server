package com.pse.auth.mail;

import java.util.Map;

import com.pse.auth.service.LoginCodeDelivery;
import com.pse.shared.util.Html;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The mail carrying a one-time password.
 *
 * <p>{@code @Profile("!test")} for the same reason the class it was split out of carried it:
 * the integration layer replaces this bean with {@code TestDeliveryConfig}'s capturing one,
 * which is how a test reads back the code it was sent.
 */
@Component
@Profile("!test")
public class LoginCodeMail implements LoginCodeDelivery {

    private static final String SUBJECT = "Your Login Code for RateMyProfApp";

    private final HtmlMailSender mailSender;
    private final MailTemplateService templates;

    /**
     * Creates LoginCodeMail.
     *
     * @param mailSender the mailSender
     * @param templates the templates
     */
    public LoginCodeMail(HtmlMailSender mailSender, MailTemplateService templates) {
        this.mailSender = mailSender;
        this.templates = templates;
    }

    @Override
    public void send(String to, String otp) {
        String body = templates.create(
                "OTPMailTemplate.html",
                Map.of("otp", Html.escape(otp, "Unknown"))
        );

        mailSender.send(to, SUBJECT, body);
    }
}
