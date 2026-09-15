package com.pse.social.service;

import java.util.Map;

/**
 * What the activity log records about a piece of submitted text.
 */
public final class ContentAudit {

    private static final int PREVIEW_LENGTH = 120;

    private ContentAudit() {
    }

    /**
     * A preview of submitted text. The full body stays in its own table; the activity
     * log only needs enough to recognise the entry.
     *
     * @param content the content
     * @return the result
     */
    public static Map<String, Object> metadataFor(String content) {
        String text = content == null ? "" : content;
        return Map.of(
                "contentPreview", text.substring(0, Math.min(text.length(), PREVIEW_LENGTH)),
                "contentLength", text.length()
        );
    }
}
