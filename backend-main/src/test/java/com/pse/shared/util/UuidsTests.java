package com.pse.shared.util;

import java.util.UUID;

import com.pse.shared.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The two shapes a bad id arrives in, and the one difference between the entry points.
 *
 * <p>Both throw for a malformed value; they disagree only about an absent one, which is a
 * missing required field in one case and "do not filter" in the other. That disagreement is
 * the whole reason there are two methods, so it is what these tests are mostly about.
 */
class UuidsTests {

    private static final String VALID = "7c9e6679-7425-40de-944b-e07fc1f90ae7";

    @Test
    void parseReturnsTheUuidForAWellFormedValue() {
        assertThat(Uuids.parse(VALID, "Invalid id")).isEqualTo(UUID.fromString(VALID));
    }

    @Test
    void parseOrNullReturnsTheUuidForAWellFormedValue() {
        assertThat(Uuids.parseOrNull(VALID, "Invalid id")).isEqualTo(UUID.fromString(VALID));
    }

    /**
     * "11111111-1111-1111-1111" is UUID-shaped and one group short. It is here because a
     * length or dash check accepts it and only a real parse does not.
     */
    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "1234", "11111111-1111-1111-1111", " "})
    void parseRejectsAMalformedValueAsABadRequest(String malformed) {
        ApiException thrown = catchThrowableOfType(
                ApiException.class, () -> Uuids.parse(malformed, "Invalid lecture id"));

        assertThat(thrown).as("nothing was thrown").isNotNull();
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(thrown.getMessage()).isEqualTo("Invalid lecture id");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "1234", "11111111-1111-1111-1111"})
    void parseOrNullRejectsAMalformedValueRatherThanTreatingItAsAbsent(String malformed) {
        ApiException thrown = catchThrowableOfType(
                ApiException.class, () -> Uuids.parseOrNull(malformed, "Invalid actor id"));

        assertThat(thrown).as("a malformed filter is still a bad request").isNotNull();
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void parseTreatsAnAbsentValueAsABadRequest(String absent) {
        ApiException thrown = catchThrowableOfType(
                ApiException.class, () -> Uuids.parse(absent, "Invalid comment id"));

        assertThat(thrown).as("a required id that is not there is the caller's fault").isNotNull();
        assertThat(thrown.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(thrown.getMessage()).isEqualTo("Invalid comment id");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void parseOrNullTreatsAnAbsentValueAsNoFilter(String absent) {
        assertThat(Uuids.parseOrNull(absent, "Invalid actor id")).isNull();
    }
}
