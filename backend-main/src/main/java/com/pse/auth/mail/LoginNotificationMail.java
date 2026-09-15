package com.pse.auth.mail;

import java.util.Map;

import com.pse.auth.location.LoginLocation;
import com.pse.auth.service.LoginLocationService;
import com.pse.auth.service.LoginSuccessDelivery;
import com.pse.shared.util.Html;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The "your account was signed in to" mail: where from, from which address, with which
 * client, and a map of the place.
 */
@Component
@Profile("!test")
public class LoginNotificationMail implements LoginSuccessDelivery {

    private static final String SUBJECT = "Successful login to your RateMyProfApp account";

    private final HtmlMailSender mailSender;
    private final MailTemplateService templates;
    private final LoginLocationService loginLocationService;
    private final LoginMapFragment mapFragment;

    /**
     * Creates LoginNotificationMail.
     *
     * @param mailSender the mailSender
     * @param templates the templates
     * @param loginLocationService the loginLocationService
     * @param mapFragment the mapFragment
     */
    public LoginNotificationMail(
            HtmlMailSender mailSender,
            MailTemplateService templates,
            LoginLocationService loginLocationService,
            LoginMapFragment mapFragment
    ) {
        this.mailSender = mailSender;
        this.templates = templates;
        this.loginLocationService = loginLocationService;
        this.mapFragment = mapFragment;
    }

    @Override
    public void send(String email, String ip, String userAgent) {

        LoginLocation location = loginLocationService.getLoginLocation(ip);

        String body = templates.create(
                "SuccessfulLoginMailTemplate.html",
                Map.of(
                        "location", Html.escape(location.description(), "Unknown"),
                        "staticMap", mapFragment.html(location),
                        "ip", Html.escape(ip, "Unknown"),
                        "userAgent", Html.escape(userAgent, "Unknown")
                )
        );

        mailSender.send(email, SUBJECT, body);
    }
}
