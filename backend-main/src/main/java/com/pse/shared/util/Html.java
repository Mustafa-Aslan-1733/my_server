package com.pse.shared.util;

import org.springframework.web.util.HtmlUtils;

/**
 * Provides Html.
 */
public final class Html {

    private Html() {
    }

    /**
     * Escapes a value before it is interpolated into generated mail HTML, substituting
     * {@code fallback} when there is nothing to show.
     *
     * <p>The fallback is a parameter because the two callers disagree about it, and used to
     * disagree invisibly: {@code LoginLocationService} and {@code MailService} each held a
     * private {@code escapeHtml} with the same name, the same body and a different answer for
     * a blank value -- {@code ""} against {@code "Unknown"}. A missing map URL should collapse
     * to nothing, a missing city should read as unknown; both are right, and neither is
     * something a reader should have to discover by opening the other class.
     *
     * @param value the value
     * @param fallback the fallback
     * @return the result
     */
    public static String escape(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return HtmlUtils.htmlEscape(value);
    }
}
