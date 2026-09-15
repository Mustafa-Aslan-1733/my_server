package com.pse.auth.mail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

/**
 * Provides MailTemplateService.
 */
@Service
public class MailTemplateService {

    private static final String TEMPLATE_DIRECTORY =  "static/mail/";

    /**
     * Returns create.
     *
     * @param templateName the templateName
     * @param values the values
     * @return the result
     */
    public String create(
            String templateName,
            Map<String, String> values
    ) {
        String template = load(templateName);

        for (Map.Entry<String, String> entry : values.entrySet()) {
            template = template.replace(
                    "{{" + entry.getKey() + "}}",
                    entry.getValue()
            );
        }

        return template;
    }

    private String load(String templateName) {
        ClassPathResource resource = new ClassPathResource(
                TEMPLATE_DIRECTORY + templateName
        );

        try {
            return StreamUtils.copyToString(
                    resource.getInputStream(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not load mail template: "
                            + templateName,
                    exception
            );
        }
    }
}
