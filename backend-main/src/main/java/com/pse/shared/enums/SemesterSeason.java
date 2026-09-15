package com.pse.shared.enums;

/**
 * Lists SemesterSeason.
 */
public enum SemesterSeason {
    /**
     * The SS.
     */
    SS,
    /**
     * The WS.
     */
    WS;

    /**
     * The semester as it is written at the university: {@code SS26}, {@code WS25/26}.
     *
     * <p>Here rather than in each client because the winter form is easy to get wrong: a
     * winter semester spans two calendar years and is written with both, while a summer
     * semester carries one. Three clients formatting this themselves is three chances to
     * publish {@code WS25}.
     *
     * <p><b>{@code year} is the year the semester starts in</b> -- {@code WS.label(2025)} is
     * {@code WS25/26}. The column stores a single {@code int} and says nothing about which of
     * the two a winter row means, so the convention is written down here and in the
     * CHANGELOG rather than left for a reader to infer from the data.
     *
     * @param year the {@code semesterYear} of the lecture
     * @return the label, without the lecture name
     */
    public String label(int year) {
        return switch (this) {
            case SS -> "SS" + twoDigits(year);
            case WS -> "WS" + twoDigits(year) + "/" + twoDigits(year + 1);
        };
    }

    /** {@code 2026 -> "26"}, {@code 2100 -> "00"}. */
    private static String twoDigits(int year) {
        return String.format("%02d", Math.floorMod(year, 100));
    }
}
