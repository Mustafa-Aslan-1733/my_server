package com.pse.lecture.mapper;

import com.pse.lecture.model.Lecture;

/**
 * Provides LectureLabels.
 */
public final class LectureLabels {

    private LectureLabels() {
    }

    /**
     * How a lecture names itself wherever one has to be shown as a single string:
     * {@code "M-INFO-101 — Algorithmen 1"}, or just the name when there is no code.
     *
     * <p>One rule, five former copies -- {@code SocialService}, {@code ModerationContentService}
     * and {@code ModerationCatalogService} each held a private {@code lectureLabel}, and
     * {@code ModerationCommentService} and {@code ModerationAnswerReportService} held the same
     * body again under the name {@code postContext}. It reaches the client as the
     * {@code postContext} field on the reported-content responses and as {@code lectureLabel}
     * on the managed-rating one, and it is the target label on every lecture-scoped audit
     * entry, so the five had to agree and nothing made them.
     *
     * <p>The separator is an em dash, not a hyphen.
     *
     * @param lecture the lecture
     * @return the result
     */
    public static String of(Lecture lecture) {
        String code = lecture.getCode();
        String name = lecture.getName();
        return code == null || code.isBlank() ? name : code + " — " + name;
    }
}
