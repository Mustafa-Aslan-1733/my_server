package com.pse;

import com.pse.rating.model.LectureType;
import com.pse.rating.model.RatingCategory;
import com.pse.shared.enums.BugSeverity;
import com.pse.shared.enums.ContentStatus;
import com.pse.shared.enums.NotificationType;
import com.pse.shared.enums.ReportReason;
import com.pse.shared.enums.SemesterSeason;
import com.pse.shared.enums.VoteType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The enum values that reach a client, pinned by name.
 *
 * <p><b>Why an enum is a contract and not an implementation detail here.</b> The Android
 * client binds every one of these by constant name through Gson, and Gson maps a value it
 * does not recognise to {@code null} rather than raising. So adding a constant on this side
 * does not produce an error in the app; it produces a {@code null} somewhere in a list, and
 * what happens next depends on whether that particular screen guards it.
 *
 * <p><b>For {@link RatingCategory} it is a crash, and the client's audit locates it exactly.</b>
 * The averages path filters nulls before rendering, but the categories path does not: the
 * screen built from {@code GET /ratings/{lectureId}/categories} calls
 * {@code category().getDisplayName()} with no guard. A new category on a lecture whose type
 * offers it therefore takes down the rating screen for that lecture. That is the whole reason
 * this class exists, and it is why the assertion is the list rather than the count -- a rename
 * is the same failure as an addition, and a count would not see it.
 *
 * <p><b>Two that are safe, written down because the opposite is the natural assumption.</b>
 * {@link VoteType#NONE} was added for vote withdrawal (ADR 0009) and the Android client
 * declares only {@code UP} and {@code DOWN} -- but it never sends {@code NONE} and no response
 * DTO carries a vote type at all, only the aggregate counters, so there is nothing for it to
 * fail to parse. And a new {@code AuditAction} is safe for the admin panel: its filter
 * dropdown is built from {@code meta.actions} rather than from a compiled-in list, and an
 * unknown action renders through a pure string transform with a default colour. Neither is
 * pinned here, because neither is a value a client would break on.
 *
 * <p>Unit-level on purpose. This is a question about declarations, and nothing about it needs
 * a Spring context, a database or an HTTP request.
 */
class ConsumerEnumContractTests {

    @Test
    void ratingCategoryHoldsExactlyTheTwentySixValuesTheAppCompilesAgainst() {
        assertThat(namesOf(RatingCategory.values()))
                .as("a RatingCategory cannot be added, removed or renamed before the Android "
                        + "client is updated. Gson maps an unrecognised value to null, and the "
                        + "categories screen dereferences it without a guard, so a new "
                        + "constant here crashes the rating screen of any lecture that offers "
                        + "it. Ship the client first, then this.")
                .containsExactly(
                        "OVERALL", "ROOM", "ORGANIZATION", "MATERIALS", "WORKLOAD",
                        "LECTURE_UNDERSTANDABILITY", "LECTURE_INTEREST", "LECTURE_DIFFICULTY",
                        "LECTURE_STRUCTURE", "LECTURE_PACE", "LECTURE_EXAMPLES",
                        "PROFESSOR_ENGAGEMENT", "PROFESSOR_COMMUNICATION",
                        "PROFESSOR_AVAILABILITY", "PROFESSOR_EXAM_PREPARATION",
                        "EXERCISE_BOARDWORK", "EXERCISE_QUESTIONS", "EXERCISE_HELPFULNESS",
                        "EXERCISE_EXPLANATIONS", "EXERCISE_EXAMPLES", "EXERCISE_PACE",
                        "TUTOR_EXPLANATION", "TUTOR_PREPARATION", "TUTOR_MOTIVATION",
                        "TUTOR_FRIENDLINESS", "TUTOR_FEEDBACK");
    }

    @Test
    void theOtherSharedEnumsHoldExactlyWhatBothClientsDeclare() {
        assertThat(namesOf(BugSeverity.values()))
                .as("sent by the app on POST /reports and rendered by the panel on GET /reports")
                .containsExactly("LOW", "MEDIUM", "HIGH", "CRITICAL");

        assertThat(namesOf(SemesterSeason.values()))
                .as("read by both clients off every lecture")
                .containsExactly("SS", "WS");

        assertThat(namesOf(ContentStatus.values()))
                .as("read by the app on comments and by the panel on moderated content")
                .containsExactly("VISIBLE", "HIDDEN", "DELETED");

        assertThat(namesOf(NotificationType.values()))
                .as("the app's notification discriminator. A third value does not crash it -- "
                        + "an unknown type deserialises to null and renders as a blank label "
                        + "with the wrong preview field -- but the row is wrong either way")
                .containsExactly("ANSWER", "WARNING");

        assertThat(namesOf(LectureType.values()))
                .as("decides which RatingCategory values a lecture offers")
                .containsExactly("LECTURE_ONLY", "LECTURE_AND_EXERCISE");

        assertThat(namesOf(ReportReason.values()))
                .as("sent by the app when reporting a comment or an answer, and rendered by "
                        + "the panel in the reported-content tables")
                .containsExactly("SPAM", "INSULT", "HARASSMENT", "HATE_SPEECH",
                        "PERSONAL_INFORMATION", "OFF_TOPIC", "INAPPROPRIATE_CONTENT", "OTHER");

        assertThat(namesOf(VoteType.values()))
                .as("NONE is this API's vote withdrawal (ADR 0009) and the Android client does "
                        + "not declare it. That is not a break: the client never sends NONE, "
                        + "and no response carries a vote type for it to fail to parse -- only "
                        + "the upVotes and downVotes counters")
                .containsExactly("UP", "DOWN", "NONE");
    }

    private static List<String> namesOf(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
