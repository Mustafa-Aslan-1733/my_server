package com.pse;

import com.pse.auth.service.AdminSuperuserPolicy;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/**
 * The addresses have to be matched the way the admin panel normalizes them — trimmed and
 * lowercased — or an operator configured with a capital letter silently has no elevation.
 */
class AdminSuperuserPolicyTests {

    @Test
    void addressesAreTrimmedAndLowercasedOnBothSides() {
        AdminSuperuserPolicy policy =
                new AdminSuperuserPolicy("  UNZHZ@Student.KIT.edu , second@student.kit.edu ");

        assertThat(policy.isSuperuser("unzhz@student.kit.edu")).isTrue();
        assertThat(policy.isSuperuser("  UNZHZ@student.kit.edu  ")).isTrue();
        assertThat(policy.isSuperuser("second@student.kit.edu")).isTrue();
        assertThat(policy.isSuperuser("someone-else@student.kit.edu")).isFalse();
    }

    @Test
    void blankEntriesAreDroppedRatherThanMatchingEveryone() {
        AdminSuperuserPolicy policy = new AdminSuperuserPolicy("a@student.kit.edu,, ,");

        assertEqualsSize(policy, 1);
        assertThat(policy.isSuperuser("")).isFalse();
        assertThat(policy.isSuperuser("   ")).isFalse();
    }

    /**
     * Unset is the safe direction: nobody is elevated, and every administrator account
     * stays protected exactly as it was before elevation existed.
     */
    @Test
    void anUnsetValueElevatesNobody() {
        AdminSuperuserPolicy policy = new AdminSuperuserPolicy("");

        assertEqualsSize(policy, 0);
        assertThat(policy.isSuperuser("unzhz@student.kit.edu")).isFalse();
        assertThat(policy.isSuperuser(null)).isFalse();
    }

    private void assertEqualsSize(AdminSuperuserPolicy policy, int expected) {
        assertThat(policy.superuserEmails().size()).isEqualTo(expected);
    }
}
