package com.pse.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The configured value decides who gets an administrator account at startup, and the admin
 * API has no other recovery path -- so a parsing slip either locks everyone out or elevates
 * an address nobody configured. The comparison is exact on purpose: the caller normalizes.
 */
class AdminBootstrapPolicyTests {

    @Test
    void bootstrapEmails_unsetConfiguration_elevatesNobody() {
        // Given
        AdminBootstrapPolicy policy = new AdminBootstrapPolicy("");

        // When / Then
        assertThat(policy.bootstrapEmails()).isEmpty();
        assertThat(policy.shouldBootstrap("unzhz@student.kit.edu")).isFalse();
    }

    @Test
    void displayName_entryWithoutAName_returnsEmptyString() {
        // Given
        AdminBootstrapPolicy policy = new AdminBootstrapPolicy("unzhz@student.kit.edu");

        // When / Then
        assertThat(policy.shouldBootstrap("unzhz@student.kit.edu")).isTrue();
        assertThat(policy.displayName("unzhz@student.kit.edu")).isEmpty();
    }

    @Test
    void displayName_entryWithAName_returnsThatName() {
        // Given
        AdminBootstrapPolicy policy = new AdminBootstrapPolicy("unzhz@student.kit.edu:Alparslan");

        // When / Then
        assertThat(policy.displayName("unzhz@student.kit.edu")).isEqualTo("Alparslan");
    }

    @Test
    void bootstrapEmails_severalEntries_keepsAllOfThem() {
        // Given
        AdminBootstrapPolicy policy =
                new AdminBootstrapPolicy("a@kit.edu:Alp,b@kit.edu,c@kit.edu:Cem");

        // When / Then
        assertThat(policy.bootstrapEmails()).containsExactlyInAnyOrder(
                "a@kit.edu", "b@kit.edu", "c@kit.edu");
        assertThat(policy.displayName("b@kit.edu")).isEmpty();
        assertThat(policy.displayName("c@kit.edu")).isEqualTo("Cem");
    }

    @Test
    void shouldBootstrap_configuredInUppercase_matchesTheLowercasedAddress() {
        // Given
        AdminBootstrapPolicy policy = new AdminBootstrapPolicy("UNZHZ@Student.KIT.edu:Alparslan");

        // When / Then
        assertThat(policy.shouldBootstrap("unzhz@student.kit.edu")).isTrue();
        assertThat(policy.displayName("unzhz@student.kit.edu")).isEqualTo("Alparslan");
    }

    /**
     * The parameter is named normalizedEmail and means it: the lookup is exact, so an
     * un-normalized argument silently misses. KitEmail lowercases before the caller asks.
     */
    @Test
    void shouldBootstrap_argumentThatIsNotNormalized_returnsFalse() {
        // Given
        AdminBootstrapPolicy policy = new AdminBootstrapPolicy("unzhz@student.kit.edu");

        // When / Then
        assertThat(policy.shouldBootstrap("UNZHZ@student.kit.edu")).isFalse();
    }

    @Test
    void bootstrapEmails_entriesPaddedWithWhitespace_areTrimmedOnBothHalves() {
        // Given
        AdminBootstrapPolicy policy = new AdminBootstrapPolicy("   a@kit.edu :  Alp   ");

        // When / Then
        assertThat(policy.bootstrapEmails()).containsExactly("a@kit.edu");
        assertThat(policy.displayName("a@kit.edu")).isEqualTo("Alp");
    }

    @Test
    void bootstrapEmails_blankEntries_areDroppedRatherThanMatchingEveryone() {
        // Given
        AdminBootstrapPolicy policy = new AdminBootstrapPolicy("a@kit.edu,, ,");

        // When / Then
        assertThat(policy.bootstrapEmails()).containsExactly("a@kit.edu");
        assertThat(policy.shouldBootstrap("")).isFalse();
        assertThat(policy.shouldBootstrap("   ")).isFalse();
    }

    /** Only the first colon separates, so a name may contain one. */
    @Test
    void displayName_nameContainingAColon_keepsEverythingAfterTheFirstOne() {
        // Given
        AdminBootstrapPolicy policy = new AdminBootstrapPolicy("a@kit.edu:Foo:Bar");

        // When / Then
        assertThat(policy.displayName("a@kit.edu")).isEqualTo("Foo:Bar");
    }

    @Test
    void bootstrapEmails_entryWithNoEmailBeforeTheColon_isSkipped() {
        // Given
        AdminBootstrapPolicy policy = new AdminBootstrapPolicy(":Name");

        // When / Then
        assertThat(policy.bootstrapEmails()).isEmpty();
    }

    @Test
    void displayName_emailConfiguredTwice_keepsTheLastName() {
        // Given
        AdminBootstrapPolicy policy = new AdminBootstrapPolicy("a@kit.edu:One,a@kit.edu:Two");

        // When / Then
        assertThat(policy.bootstrapEmails()).containsExactly("a@kit.edu");
        assertThat(policy.displayName("a@kit.edu")).isEqualTo("Two");
    }

    @ParameterizedTest
    @CsvSource({"someone-else@kit.edu", "'' ", "a@kit.ed"})
    void shouldBootstrap_addressThatWasNotConfigured_returnsFalse(String candidate) {
        // Given
        AdminBootstrapPolicy policy = new AdminBootstrapPolicy("a@kit.edu:Alp");

        // When / Then
        assertThat(policy.shouldBootstrap(candidate)).isFalse();
        assertThat(policy.displayName(candidate)).isEmpty();
    }

    @Test
    void bootstrapEmails_returnedSet_isUnmodifiable() {
        // Given
        AdminBootstrapPolicy policy = new AdminBootstrapPolicy("a@kit.edu");

        // When / Then -- the caller iterates it at startup and must not be able to edit it
        assertThatThrownBy(() -> policy.bootstrapEmails().add("b@kit.edu"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void constructor_nullConfiguration_throwsNullPointerException() {
        // When / Then -- @Value defaults to "", so null only happens if wiring is wrong
        assertThatThrownBy(() -> new AdminBootstrapPolicy(null))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * The accounts live in an immutable Map, which rejects a null key outright. Pinned as
     * current behaviour: every caller passes an address it has already normalized, so a null
     * here means the caller is broken and should say so loudly rather than answer "no".
     */
    @Test
    void shouldBootstrap_null_throwsNullPointerException() {
        // Given
        AdminBootstrapPolicy policy = new AdminBootstrapPolicy("a@kit.edu");

        // When / Then
        assertThatThrownBy(() -> policy.shouldBootstrap(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void displayName_null_throwsNullPointerException() {
        // Given
        AdminBootstrapPolicy policy = new AdminBootstrapPolicy("a@kit.edu");

        // When / Then
        assertThatThrownBy(() -> policy.displayName(null))
                .isInstanceOf(NullPointerException.class);
    }
}
