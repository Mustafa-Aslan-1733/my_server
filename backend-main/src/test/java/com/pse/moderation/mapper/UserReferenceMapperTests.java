package com.pse.moderation.mapper;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.pse.moderation.dto.response.UserReferenceResponse;
import com.pse.shared.enums.UserRole;
import com.pse.shared.enums.UserStatus;
import com.pse.user.model.Student;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The author of reported content, as the panel shows them.
 *
 * <p>Written because nothing pinned the two derived fields. There were three copies of this
 * mapping and the role and warning count came out of each of them; the API tests assert the
 * author's id and name and stop there, so replacing two per-row lookups with the batched
 * shape could have changed either field without a test noticing.
 */
class UserReferenceMapperTests {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void toReference_studentInTheAdminSet_isReportedAsAnAdministrator() {
        UserReferenceResponse reference =
                UserReferenceMapper.toReference(student(), Set.of(ID), Map.of());

        assertThat(reference.role()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void toReference_studentNotInTheAdminSet_isReportedAsAStudent() {
        UserReferenceResponse reference =
                UserReferenceMapper.toReference(student(), Set.of(UUID.randomUUID()), Map.of());

        assertThat(reference.role()).isEqualTo(UserRole.STUDENT);
    }

    @Test
    void toReference_warningCountForThisStudent_isTheOneReported() {
        UserReferenceResponse reference = UserReferenceMapper.toReference(
                student(), Set.of(), Map.of(ID, 3, UUID.randomUUID(), 99));

        assertThat(reference.warnings()).isEqualTo(3);
    }

    /** An account nobody has warned is absent from the grouped count, not present with a 0. */
    @Test
    void toReference_studentMissingFromTheCounts_hasNoWarnings() {
        UserReferenceResponse reference =
                UserReferenceMapper.toReference(student(), Set.of(), Map.of());

        assertThat(reference.warnings()).isZero();
    }

    @Test
    void toReference_carriesTheIdentityFieldsUnchanged() {
        UserReferenceResponse reference =
                UserReferenceMapper.toReference(student(), Set.of(), Map.of());

        assertThat(reference.id()).isEqualTo(ID);
        assertThat(reference.name()).isEqualTo("ada");
        assertThat(reference.status()).isEqualTo(UserStatus.ACTIVE);
    }

    private static Student student() {
        Student student = new Student();
        student.setId(ID);
        student.setUsername("ada");
        student.setStatus(UserStatus.ACTIVE);
        return student;
    }
}
