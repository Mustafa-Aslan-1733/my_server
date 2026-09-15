package com.pse.social.service;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The preview the activity log keeps of submitted text. Extracted from
 * {@code SocialService.contentMetadata}, where it was reached only through the two submit
 * paths and its boundary was never stated.
 */
class ContentAuditTests {

    @Test
    void metadataFor_shortText_isCarriedWhole() {
        Map<String, Object> metadata = ContentAudit.metadataFor("Great lecture");

        assertThat(metadata).containsEntry("contentPreview", "Great lecture");
        assertThat(metadata).containsEntry("contentLength", 13);
    }

    @Test
    void metadataFor_textAtTheLimit_isNotTruncated() {
        String text = "x".repeat(120);

        assertThat(ContentAudit.metadataFor(text)).containsEntry("contentPreview", text);
    }

    @Test
    void metadataFor_longerText_keepsTheFirst120CharactersAndTheRealLength() {
        String text = "x".repeat(500);

        Map<String, Object> metadata = ContentAudit.metadataFor(text);

        // The length is of the submission, not of the preview: the log says how much was
        // written even though it only stores enough to recognise the entry.
        assertThat(metadata).containsEntry("contentPreview", "x".repeat(120));
        assertThat(metadata).containsEntry("contentLength", 500);
    }

    @Test
    void metadataFor_null_isEmptyRatherThanAFailedAuditWrite() {
        Map<String, Object> metadata = ContentAudit.metadataFor(null);

        assertThat(metadata).containsEntry("contentPreview", "");
        assertThat(metadata).containsEntry("contentLength", 0);
    }
}
