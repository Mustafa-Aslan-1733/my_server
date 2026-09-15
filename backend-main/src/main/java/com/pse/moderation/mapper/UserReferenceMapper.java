package com.pse.moderation.mapper;

import com.pse.moderation.dto.response.UserReferenceResponse;
import com.pse.shared.enums.UserRole;
import com.pse.user.model.Student;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The author of a piece of reported or listed content, as the panel shows them.
 *
 * <p>Static and given its counts rather than looking them up: there were three copies of this
 * mapping, and two of them queried {@code existsByStudent} and {@code countByStudent} once per
 * row. The listing already batches both, so the batched shape is the one that survives and
 * the callers pass what they have.
 */
public final class UserReferenceMapper {

    private UserReferenceMapper() {
    }

    /**
     * Returns toReference.
     *
     * @param student the student
     * @param adminIds the adminIds
     * @param warningCounts the warningCounts
     * @return the result
     */
    public static UserReferenceResponse toReference(
            Student student,
            Set<UUID> adminIds,
            Map<UUID, Integer> warningCounts
    ) {
        return new UserReferenceResponse(
                student.getId(),
                student.getUsername(),
                adminIds.contains(student.getId()) ? UserRole.ADMIN : UserRole.STUDENT,
                warningCounts.getOrDefault(student.getId(), 0),
                student.getStatus()
        );
    }
}
