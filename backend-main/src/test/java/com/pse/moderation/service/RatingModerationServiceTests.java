package com.pse.moderation.service;

import com.pse.audit.service.AuditWriter;
import org.mockito.junit.jupiter.MockitoExtension;
import com.pse.moderation.repository.AdminRepository;
import com.pse.moderation.repository.WarningRepository;
import com.pse.rating.repository.RatingRepository;
import com.pse.shared.error.ApiException;
import com.pse.shared.util.KeysetCursorCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;


import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The query parameters of the ratings listing, every one of which is answered before a query
 * runs.
 *
 * <p>Split out of {@code ModerationContentServiceTests} with the code it covers.
 */
@ExtendWith(MockitoExtension.class)
class RatingModerationServiceTests {

    @Mock private RatingRepository ratingRepository;
    @Mock private WarningRepository warningRepository;
    @Mock private AdminRepository adminRepository;
    @Mock private AuditWriter auditWriter;
    @Mock private KeysetCursorCodec cursorCodec;

    private RatingModerationService service() {
        return new RatingModerationService(
                ratingRepository, warningRepository, adminRepository, auditWriter, cursorCodec);
    }


    // ------------------------------------------------- the ratings query parameters

    @ParameterizedTest
    @ValueSource(strings = {"nine", "0", "-1", "101", "1.5"})
    void getRatings_limitThatIsNotAWholeNumberInRange_isRejectedWithoutQuerying(String rawLimit) {
        assertThatThrownBy(() -> service().getRatings(rawLimit, null, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("Limit must be an integer between 1 and 100");

        verifyNoInteractions(ratingRepository, adminRepository, cursorCodec);
    }


    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "1234", "11111111-1111-1111-1111"})
    void getRatings_lectureIdThatIsNotAUuid_isRejected(String rawLectureId) {
        assertThatThrownBy(() -> service().getRatings(null, null, rawLectureId))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid lecture id");

        verifyNoInteractions(ratingRepository, cursorCodec);
    }


    @Test
    void getRatings_cursorWithoutALimit_isRejectedBeforeTheCursorIsDecoded() {
        assertThatThrownBy(() -> service().getRatings(null, "any-cursor", null))
                .isInstanceOf(ApiException.class)
                .hasMessage("A cursor requires a limit");

        verifyNoInteractions(cursorCodec, ratingRepository);
    }
}
