package com.pse.professor.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A professor as the catalogue reads return them.
 *
 * @param lectureIds the lectures this professor teaches. The assignment is writable through
 *                   {@code PATCH /data/professor/{id}}, so reading it back from the professor
 *                   rather than reverse-indexing it from the lecture list is what keeps the
 *                   two directions agreeing once the lecture list is paginated or filtered.
 *
 * @param id the id
 * @param firstName the firstName
 * @param lastName the lastName
 * @param active the active
 * @param averageRating the averageRating
 * @param ratingCount the ratingCount
 */
public record ProfessorResponse(
        UUID id,
        String firstName,
        String lastName,
        boolean active,
        BigDecimal averageRating,
        int ratingCount,
        List<UUID> lectureIds
) {
}
