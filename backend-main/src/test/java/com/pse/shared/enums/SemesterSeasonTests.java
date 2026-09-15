package com.pse.shared.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The label the API publishes as {@code semesterLabel}, and the reason it is computed in one
 * place: a winter semester is written with both of the years it spans, a summer semester with
 * one, and a client that gets that wrong publishes {@code WS25} to its users.
 */
class SemesterSeasonTests {

    @Test
    void summerSemesterIsTheSeasonAndTwoDigitsOfItsOnlyYear() {
        assertThat(SemesterSeason.SS.label(2026)).isEqualTo("SS26");
    }

    @Test
    void winterSemesterCarriesBothOfTheYearsItSpans() {
        assertThat(SemesterSeason.WS.label(2025)).isEqualTo("WS25/26");
    }

    /** The year is the one the semester starts in, which is the whole ambiguity. */
    @Test
    void winterSemesterCountsForwardFromTheStoredYear() {
        assertThat(SemesterSeason.WS.label(2026)).isEqualTo("WS26/27");
    }

    /** A century boundary is the only place two-digit years can produce nonsense. */
    @Test
    void winterSemesterAcrossACenturyKeepsBothHalvesTwoDigits() {
        assertThat(SemesterSeason.WS.label(2099)).isEqualTo("WS99/00");
    }

    @Test
    void aSingleDigitYearIsPaddedRatherThanPrintedBare() {
        assertThat(SemesterSeason.SS.label(2005)).isEqualTo("SS05");
    }
}
