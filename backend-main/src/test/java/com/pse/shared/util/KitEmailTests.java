package com.pse.shared.util;

import com.pse.shared.error.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The address rule three call sites used to each keep a copy of -- two in {@code auth}, one in
 * {@code moderation}, byte for byte the same pattern and the same message. Tested here so the
 * one that remains is pinned in its own right rather than through whichever service happens
 * to call it.
 */
class KitEmailTests {

    // ------------------------------------------------------------------ normalize

    @Test
    void normalize_mixedCaseAndPadding_isTrimmedAndLowercased() {
        assertThat(KitEmail.normalize("  Firstname.Lastname@Student.KIT.edu  "))
                .isEqualTo("firstname.lastname@student.kit.edu");
    }

    @Test
    void normalize_null_staysNull() {
        assertThat(KitEmail.normalize(null)).isNull();
    }

    @Test
    void normalize_doesNotJudgeTheAddress() {
        // The two operations are separate on purpose: validate() only ever wanted this half.
        assertThat(KitEmail.normalize(" NOT-AN-ADDRESS ")).isEqualTo("not-an-address");
    }

    // ------------------------------------------------------------------ requireKitAddress

    @ParameterizedTest
    @ValueSource(strings = {
            "student@student.kit.edu",
            "  Student@Student.KIT.edu  ",
            "first.last+tag@student.kit.edu",
            "a_b-c%d@student.kit.edu"
    })
    void requireKitAddress_aKitStudentAddress_isAcceptedAndNormalized(String raw) {
        assertThat(KitEmail.requireKitAddress(raw)).isEqualTo(raw.trim().toLowerCase());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "   ",
            "student@kit.edu",
            "student@student.kit.edu.evil.com",
            "student@sub.student.kit.edu",
            "@student.kit.edu",
            "student at student.kit.edu"
    })
    void requireKitAddress_anythingElse_isRefusedAsABadRequest(String raw) {
        assertThatThrownBy(() -> KitEmail.requireKitAddress(raw))
                .isInstanceOf(ApiException.class)
                .hasMessage("Invalid KIT email")
                .extracting(thrown -> ((ApiException) thrown).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
