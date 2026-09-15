package com.pse.moderation.service;

import com.pse.shared.enums.ContentStatus;
import com.pse.shared.enums.ReportStatus;

/**
 * What resolving a report does to the content it was filed against.
 *
 * <p>The one rule the comment-report and answer-report services must agree on, and the one
 * they each kept their own copy of. {@code ACTION_TAKEN} is the only status that means "this
 * content was moderated", so it is the only one that hides the content; moving the report to
 * any other status puts it back. Answers are nested under the comment in the student read
 * path, so they disappear and return with it.
 *
 * <p>The rest of what those two classes have in common is plumbing the compiler keeps honest
 * -- different tables, different repositories, different audit constants. This is the part
 * that could silently diverge, so this is the part with a name.
 */
public final class ReportOutcome {

    private ReportOutcome() {
    }

    /**
     * Returns visibilityFor.
     *
     * @param status the status
     * @return the result
     */
    public static ContentStatus visibilityFor(ReportStatus status) {
        return status == ReportStatus.ACTION_TAKEN ? ContentStatus.HIDDEN : ContentStatus.VISIBLE;
    }
}
