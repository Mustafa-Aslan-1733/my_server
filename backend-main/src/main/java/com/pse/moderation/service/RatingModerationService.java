package com.pse.moderation.service;

import com.pse.shared.util.Uuids;
import com.pse.audit.model.AuditAction;
import com.pse.audit.model.AuditTargetType;
import com.pse.audit.service.AuditWriter;
import com.pse.lecture.mapper.LectureLabels;
import com.pse.moderation.dto.response.ManagedRatingResponse;
import com.pse.moderation.dto.response.ManagedRatingTopicResponse;
import com.pse.moderation.dto.response.ManagedRatingsResponse;
import com.pse.moderation.repository.AdminRepository;
import com.pse.moderation.repository.WarningRepository;
import com.pse.rating.model.Rating;
import com.pse.rating.model.RatingTopic;
import com.pse.rating.repository.RatingRepository;
import com.pse.security.AuthenticatedUser;
import com.pse.shared.dto.BasicResponse;
import com.pse.shared.error.ApiException;
import com.pse.shared.page.KeysetPage;
import com.pse.shared.repository.IdCount;
import com.pse.shared.util.KeysetCursorCodec;
import com.pse.shared.util.UtcDates;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pse.moderation.mapper.UserReferenceMapper;

/**
 * The ratings listing the panel pages through, and the removal of a single rating.
 *
 * <p>Its own class because it is its own axis: a different table, a different controller, and
 * the only keyset-paginated read in this part of the module.
 */
@Service
public class RatingModerationService {

    private final RatingRepository ratingRepository;
    private final WarningRepository warningRepository;
    private final AdminRepository adminRepository;
    private final AuditWriter auditWriter;
    private final KeysetCursorCodec cursorCodec;

    /**
     * Creates RatingModerationService.
     *
     * @param ratingRepository the ratingRepository
     * @param warningRepository the warningRepository
     * @param adminRepository the adminRepository
     * @param auditWriter the auditWriter
     * @param cursorCodec the cursorCodec
     */
    public RatingModerationService(
            RatingRepository ratingRepository,
            WarningRepository warningRepository,
            AdminRepository adminRepository,
            AuditWriter auditWriter,
            KeysetCursorCodec cursorCodec
    ) {
        this.ratingRepository = ratingRepository;
        this.warningRepository = warningRepository;
        this.adminRepository = adminRepository;
        this.auditWriter = auditWriter;
        this.cursorCodec = cursorCodec;
    }

    /**
     * Every rating individually, which is what a bogus one gets identified and deleted by id
     * from. Filtering by lecture is a query parameter rather than a path segment because the
     * public per-lecture averages already own {@code GET /ratings/{lectureId}}.
     *
     * <p>Pagination is opt-in in the same way {@code GET /users} is: no {@code limit} returns
     * everything and a null cursor, so a caller that has not learned to follow cursors is not
     * silently truncated.
     *
     * @param rawLimit the rawLimit
     * @param rawCursor the rawCursor
     * @param rawLectureId the rawLectureId
     * @return the result
     */
    @Transactional(readOnly = true)
    public ManagedRatingsResponse getRatings(String rawLimit, String rawCursor, String rawLectureId) {
        Integer limit = KeysetPage.optionalLimit(rawLimit);
        UUID lectureId = parseLectureId(rawLectureId);
        boolean hasCursor = rawCursor != null && !rawCursor.isBlank();
        KeysetPage.requireLimitWithCursor(hasCursor, limit);
        KeysetCursorCodec.Cursor cursor = hasCursor
                ? cursorCodec.decode(rawCursor, "Invalid rating cursor")
                : null;

        Set<UUID> adminIds = Set.copyOf(adminRepository.findAllAdminStudentIds());
        Map<UUID, Integer> warningCounts = IdCount.asMap(warningRepository.countGroupedByStudent());

        Specification<Rating> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (lectureId != null) {
                predicates.add(builder.equal(root.get("lecture").get("id"), lectureId));
            }
            if (cursor != null) {
                predicates.add(KeysetPage.after(
                        builder, root, cursor.createdAtLocal(), cursor.id(), Sort.Direction.DESC));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };

        List<Rating> page;
        String nextCursor = null;
        if (limit == null) {
            page = ratingRepository.findAll(
                    specification,
                    KeysetPage.byCreatedAtThenId(Sort.Direction.DESC)
            );
        } else {
            List<Rating> fetched = ratingRepository.findAll(
                    specification,
                    PageRequest.of(0, limit + 1, KeysetPage.byCreatedAtThenId(Sort.Direction.DESC))
            ).getContent();
            KeysetPage.Slice<Rating> slice = KeysetPage.of(fetched, limit,
                    last -> cursorCodec.encode(last.getCreatedAt(), last.getId()));
            page = slice.items();
            nextCursor = slice.nextCursor();
        }

        List<ManagedRatingResponse> ratings = page.stream()
                .map(rating -> new ManagedRatingResponse(
                        rating.getId(),
                        UserReferenceMapper.toReference(rating.getStudent(), adminIds, warningCounts),
                        LectureLabels.of(rating.getLecture()),
                        rating.getLecture().getId(),
                        // Declaration order, like the two app-tier reads. The bag has no
                        // @OrderBy, so without this the admin list reshuffles too -- F-27.
                        rating.getTopics().stream()
                                .sorted(Comparator.comparing(RatingTopic::getCategory))
                                .map(topic -> new ManagedRatingTopicResponse(
                                        topic.getCategory(),
                                        topic.getValue()
                                ))
                                .toList(),
                        UtcDates.format(rating.getCreatedAt())
                ))
                .toList();
        return new ManagedRatingsResponse(
                "Successfully found all ratings", true, ratings, nextCursor);
    }

    private static UUID parseLectureId(String rawLectureId) {
        // Trimmed at the call site rather than inside the helper: this route has always
        // accepted a padded id and the others have always refused one, and quietly making
        // them agree would change an answer nobody asked about.
        return Uuids.parseOrNull(
                rawLectureId == null ? null : rawLectureId.trim(), "Invalid lecture id");
    }

    /**
     * Discards a rating. Deliberately delete-only: rewriting someone's scores would put
     * an opinion they never gave into the lecture average, so a bogus rating is removed
     * rather than corrected.
     *
     * @param principal the principal
     * @param ratingId the ratingId
     * @return the result
     */
    @Transactional
    public BasicResponse deleteRating(AuthenticatedUser principal, UUID ratingId) {
        Rating rating = ratingRepository.findById(ratingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Rating not found"));
        String label = "Rating by " + rating.getStudent().getUsername()
                + " for " + LectureLabels.of(rating.getLecture());
        int topics = rating.getTopics().size();

        // The topic rows are cascaded from the rating.
        ratingRepository.delete(rating);

        Map<String, Object> changes = AuditWriter.lifecycle(true, false);
        auditWriter.write(
                principal.admin(),
                AuditAction.RATING_DELETED,
                AuditTargetType.RATING,
                ratingId,
                label,
                changes,
                Map.of("topicCount", topics)
        );
        return new BasicResponse("Deleted rating successfully", true);
    }
}
