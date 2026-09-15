package com.pse.moderation.service.user;

import com.pse.moderation.mapper.UserResponseMapper;
import com.pse.moderation.repository.AdminRepository;
import com.pse.moderation.repository.WarningRepository;
import com.pse.shared.error.ApiException;
import com.pse.shared.util.KeysetCursorCodec;
import com.pse.social.repository.CommentReportRepository;
import com.pse.user.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The query parameters of {@code GET /users}, every one of which is answered before a query runs.
 *
 * <p>Split out of {@code ModerationUserServiceTests} with the code it covers.
 */
@ExtendWith(MockitoExtension.class)class UserDirectoryServiceTests {

    @Mock private StudentRepository studentRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private WarningRepository warningRepository;
    @Mock private CommentReportRepository commentReportRepository;
    @Mock private KeysetCursorCodec cursorCodec;


    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-07T10:15:30Z"), ZoneOffset.UTC);

    private static final UUID TARGET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID CALLER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private UserDirectoryService directory() {
        return new UserDirectoryService(studentRepository, adminRepository, warningRepository,
                commentReportRepository, cursorCodec, mapper());
    }

    /** Real for the same reason {@code moderatedStudents()} is. */
    private UserResponseMapper mapper() {
        return new UserResponseMapper(
                adminRepository, warningRepository, commentReportRepository, CLOCK);
    }

    // --------------------------------------------- query parameters, before any query

    /**
     * Every one of these is rejected before the first repository call, which is why no
     * stubbing is needed and why the assertion that nothing was queried is worth making:
     * a malformed page request must not cost a database round trip.
     */
    @ParameterizedTest
    @ValueSource(strings = {"nine", "0", "-1", "101", "1.5"})
    void getStudents_limitThatIsNotAWholeNumberInRange_isRejectedWithoutQuerying(String rawLimit) {
        assertThatThrownBy(() -> directory().getStudents(rawLimit, null, null, null, null, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("Limit must be an integer between 1 and 100")
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(studentRepository, adminRepository, cursorCodec);
    }

    @Test
    void getStudents_cursorWithoutALimit_isRejectedBeforeTheCursorIsEvenDecoded() {
        assertThatThrownBy(() -> directory().getStudents(null, "any-cursor", null, null, null, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("A cursor requires a limit");

        // How the request is formed is the more fundamental complaint than what the cursor
        // contains, so the codec must never be reached.
        verifyNoInteractions(cursorCodec, studentRepository);
    }

    @Test
    void getStudents_unknownStatusFilter_isRejected() {
        assertThatThrownBy(() -> directory().getStudents(null, null, null, "ASLEEP", null, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid user status");
    }

    /**
     * Inverted, not deleted. This used to pin a {@code 400 "Deleted users are not listed"}:
     * the listing excluded deleted accounts unconditionally, so the filter could only ever
     * have returned an empty page, and an empty page reads as "no deleted accounts exist"
     * rather than "you cannot ask that". The exclusion is conditional now and this filter is
     * the only way to lift it, so the question is answerable and the refusal is gone.
     *
     * <p>The spellings are here for the same reason they are on the sort parser: the parse
     * trims and upper-cases, so a client sending {@code Deleted} must not fall into the
     * unknown-status branch.
     */
    @ParameterizedTest
    @ValueSource(strings = {"deleted", "DELETED", "  Deleted  "})
    void getStudents_filteringForDeletedUsers_reachesTheQuery(String rawStatus) {
        when(adminRepository.findAllAdminStudentIds()).thenReturn(List.of());
        when(studentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of());

        directory().getStudents(null, null, null, rawStatus, null, null);

        verify(studentRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class));
    }

    @Test
    void getStudents_unknownRoleFilter_isRejected() {
        assertThatThrownBy(() -> directory().getStudents(null, null, null, null, "MODERATOR", null))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid user role");
    }

    @ParameterizedTest
    @ValueSource(strings = {"sideways", "NEWEST_FIRST", "asc", "new"})
    void getStudents_unknownSortOrder_isRejected(String rawSort) {
        assertThatThrownBy(() -> directory().getStudents(null, null, null, null, null, rawSort))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid user sort");
    }

    /**
     * The accepted spellings, pinned because the parser is case-insensitive and trims: a
     * client sending "Newest" must not fall into the rejection branch. A blank value is in
     * this list rather than in the rejections above -- it means "unspecified", so it takes
     * the newest-first default rather than being an invalid sort.
     */
    @ParameterizedTest
    @ValueSource(strings = {"newest", "NEWEST", "  Newest  ", "oldest", "OLDEST", "oldest ", " "})
    void getStudents_acceptedSortSpellings_reachTheQuery(String rawSort) {
        when(adminRepository.findAllAdminStudentIds()).thenReturn(List.of());
        when(studentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of());

        directory().getStudents(null, null, null, null, null, rawSort);

        verify(studentRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class));
    }

    @Test
    void getStudents_searchLongerThanTheLimit_isRejected() {
        assertThatThrownBy(() ->
                directory().getStudents(null, null, "x".repeat(201), null, null, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("User search is too long");
    }
}
