package com.pse.shared.page;

import java.util.List;

import com.pse.shared.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Three listings resume their pages through this class, and a boundary error here drops or
 * repeats a row on every one of them. Tested directly rather than through those listings,
 * which reach it with mocked repositories and so never execute the slice at all.
 *
 * <p>{@code after} is deliberately absent: it builds a JPA {@code Predicate}, which nothing
 * evaluates without a real {@code EntityManager}. {@code CursorPaginationPostgresTests} is
 * where that is exercised, against a database that can actually order rows.
 */
class KeysetPageTests {

    // ------------------------------------------------------------------ optionalLimit

    @Test
    void optionalLimit_absent_meansDoNotPaginate() {
        assertThat(KeysetPage.optionalLimit(null)).isNull();
        assertThat(KeysetPage.optionalLimit("   ")).isNull();
    }

    @Test
    void optionalLimit_aNumberInRange_isThatNumber() {
        assertThat(KeysetPage.optionalLimit(" 25 ")).isEqualTo(25);
    }

    // ------------------------------------------------------------------ limitOr

    @Test
    void limitOr_absent_isTheFallbackAndNotTheBound() {
        assertThat(KeysetPage.limitOr(null, 50)).isEqualTo(50);
        assertThat(KeysetPage.limitOr("", 50)).isEqualTo(50);
    }

    @Test
    void limitOr_aNumberInRange_overridesTheFallback() {
        assertThat(KeysetPage.limitOr("7", 50)).isEqualTo(7);
    }

    // ------------------------------------------------------------------ the bounds

    @ParameterizedTest
    @ValueSource(strings = {"1", "100"})
    void limit_atEitherEndOfTheRange_isAccepted(String raw) {
        assertThat(KeysetPage.optionalLimit(raw)).isEqualTo(Integer.valueOf(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "101", "abc", "1.5", "9999999999"})
    void limit_outsideTheRangeOrNotANumber_isRefusedTheSameWay(String raw) {
        assertThatThrownBy(() -> KeysetPage.optionalLimit(raw))
                .isInstanceOf(ApiException.class)
                .hasMessage("Limit must be an integer between 1 and 100")
                .extracting(thrown -> ((ApiException) thrown).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ------------------------------------------------------------------ requireLimitWithCursor

    @Test
    void requireLimitWithCursor_cursorWithoutALimit_isRefused() {
        assertThatThrownBy(() -> KeysetPage.requireLimitWithCursor(true, null))
                .isInstanceOf(ApiException.class)
                .hasMessage("A cursor requires a limit");
    }

    @Test
    void requireLimitWithCursor_everyOtherCombination_isAllowed() {
        assertThatCode(() -> KeysetPage.requireLimitWithCursor(true, 10)).doesNotThrowAnyException();
        assertThatCode(() -> KeysetPage.requireLimitWithCursor(false, null)).doesNotThrowAnyException();
        assertThatCode(() -> KeysetPage.requireLimitWithCursor(false, 10)).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------ byCreatedAtThenId

    @Test
    void byCreatedAtThenId_ordersOnBothColumnsInTheGivenDirection() {
        assertThat(KeysetPage.byCreatedAtThenId(Sort.Direction.DESC))
                .containsExactly(
                        new Sort.Order(Sort.Direction.DESC, "createdAt"),
                        new Sort.Order(Sort.Direction.DESC, "id"));
        assertThat(KeysetPage.byCreatedAtThenId(Sort.Direction.ASC))
                .containsExactly(
                        new Sort.Order(Sort.Direction.ASC, "createdAt"),
                        new Sort.Order(Sort.Direction.ASC, "id"));
    }

    // ------------------------------------------------------------------ of

    @Test
    void of_oneRowMoreThanAsked_isCutBackAndReportsANextPage() {
        KeysetPage.Slice<String> slice = KeysetPage.of(List.of("a", "b", "c"), 2, last -> "at:" + last);

        assertThat(slice.items()).containsExactly("a", "b");
        assertThat(slice.nextCursor()).isEqualTo("at:b");
    }

    /** Exactly a full page is the last page: the extra row is what proves there is another. */
    @Test
    void of_exactlyAFullPage_hasNoNextCursor() {
        KeysetPage.Slice<String> slice = KeysetPage.of(List.of("a", "b"), 2, last -> "at:" + last);

        assertThat(slice.items()).containsExactly("a", "b");
        assertThat(slice.nextCursor()).isNull();
    }

    @Test
    void of_fewerRowsThanAsked_hasNoNextCursor() {
        KeysetPage.Slice<String> slice = KeysetPage.of(List.of("a"), 2, last -> "at:" + last);

        assertThat(slice.items()).containsExactly("a");
        assertThat(slice.nextCursor()).isNull();
    }

    @Test
    void of_noRowsAtAll_hasNoNextCursor() {
        KeysetPage.Slice<String> slice = KeysetPage.of(List.of(), 2, last -> "at:" + last);

        assertThat(slice.items()).isEmpty();
        assertThat(slice.nextCursor()).isNull();
    }

    /**
     * A limit of zero cannot come from {@link KeysetPage#optionalLimit}, which floors at one.
     * Pinned anyway because the encoder must not be handed an element the page does not
     * contain -- {@code page.getLast()} on an empty page would throw.
     */
    @Test
    void of_aLimitOfZero_returnsNothingAndDoesNotEncodeACursor() {
        KeysetPage.Slice<String> slice = KeysetPage.of(List.of("a"), 0, last -> {
            throw new AssertionError("the cursor must not be encoded from an empty page");
        });

        assertThat(slice.items()).isEmpty();
        assertThat(slice.nextCursor()).isNull();
    }
}
