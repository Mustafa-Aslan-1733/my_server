package com.pse.moderation.service.user;

import com.pse.moderation.dto.response.UserListResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.pse.moderation.dto.response.UserResponse;
import com.pse.moderation.mapper.UserResponseMapper;
import com.pse.moderation.repository.AdminRepository;
import com.pse.moderation.repository.WarningRepository;
import com.pse.shared.enums.UserRole;
import com.pse.shared.enums.UserStatus;
import com.pse.shared.error.ApiException;
import com.pse.shared.page.KeysetPage;
import com.pse.shared.repository.IdCount;
import com.pse.shared.util.KeysetCursorCodec;
import com.pse.shared.util.SqlLike;
import com.pse.social.repository.CommentReportRepository;
import com.pse.user.model.Student;
import com.pse.user.repository.StudentRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

/**
 * Reading accounts: the searchable, keyset-paginated listing the panel shows, and one account
 * on its own.
 */
@Service
public class UserDirectoryService {

    private final StudentRepository studentRepository;
    private final AdminRepository adminRepository;
    private final WarningRepository warningRepository;
    private final CommentReportRepository commentReportRepository;
    private final KeysetCursorCodec cursorCodec;
    private final UserResponseMapper mapper;

    /**
     * Creates UserDirectoryService.
     *
     * @param studentRepository the studentRepository
     * @param adminRepository the adminRepository
     * @param warningRepository the warningRepository
     * @param commentReportRepository the commentReportRepository
     * @param cursorCodec the cursorCodec
     * @param mapper the mapper
     */
    public UserDirectoryService(
            StudentRepository studentRepository,
            AdminRepository adminRepository,
            WarningRepository warningRepository,
            CommentReportRepository commentReportRepository,
            KeysetCursorCodec cursorCodec,
            UserResponseMapper mapper
    ) {
        this.studentRepository = studentRepository;
        this.adminRepository = adminRepository;
        this.warningRepository = warningRepository;
        this.commentReportRepository = commentReportRepository;
        this.cursorCodec = cursorCodec;
        this.mapper = mapper;
    }

    /**
     * Returns getStudents.
     *
     * @param rawLimit the rawLimit
     * @param rawCursor the rawCursor
     * @param q the q
     * @param rawStatus the rawStatus
     * @param rawRole the rawRole
     * @param rawSort the rawSort
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    @Transactional(readOnly = true)
    public UserListResponse getStudents(
            String rawLimit,
            String rawCursor,
            String q,
            String rawStatus,
            String rawRole,
            String rawSort
    ) {
        Integer limit = KeysetPage.optionalLimit(rawLimit);
        UserStatus status = UserListQuery.parseStatusFilter(rawStatus);
        UserRole role = UserListQuery.parseUserRole(rawRole);
        Sort.Direction direction = UserListQuery.parseSortDirection(rawSort);
        boolean hasCursor = rawCursor != null && !rawCursor.isBlank();
        KeysetPage.requireLimitWithCursor(hasCursor, limit);
        KeysetCursorCodec.Cursor cursor = hasCursor
                ? cursorCodec.decode(rawCursor, "Invalid user cursor")
                : null;
        String search = q == null ? "" : q.trim();
        if (search.length() > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "User search is too long");
        }

        // Three batch queries instead of three per student.
        Set<UUID> adminIds = Set.copyOf(adminRepository.findAllAdminStudentIds());
        Map<UUID, Integer> warningCounts = IdCount.asMap(warningRepository.countGroupedByStudent());
        Map<UUID, Integer> reportCounts =
                IdCount.asMap(commentReportRepository.countGroupedByCommentStudent());

        Specification<Student> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            // Soft-deleted accounts stay out of every listing that did not name them, and
            // the default has to go on excluding them: the panel reads its account counts
            // off this listing, and counting anonymised rows would over-report the size of
            // the platform. Asking for them by status is the one way to lift it, so the
            // exclusion is skipped exactly then -- and the equality predicate below is what
            // narrows the page, so lifting it never widens anything on its own.
            if (status != UserStatus.DELETED) {
                predicates.add(builder.notEqual(root.get("status"), UserStatus.DELETED));
            }
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (role != null) {
                predicates.add(UserListQuery.rolePredicate(builder, root, adminIds, role));
            }
            if (!search.isEmpty()) {
                String pattern = "%" + SqlLike.escape(search.toLowerCase(Locale.ROOT)) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("username")), pattern, SqlLike.ESCAPE),
                        builder.like(builder.lower(root.get("kitEmail")), pattern, SqlLike.ESCAPE)
                ));
            }
            if (cursor != null) {
                predicates.add(KeysetPage.after(
                        builder, root, cursor.createdAtLocal(), cursor.id(), direction));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };

        if (limit == null) {
            List<UserResponse> users = studentRepository.findAll(specification).stream()
                    .map(student -> mapper.toResponse(
                            student,
                            adminIds.contains(student.getId()),
                            warningCounts.getOrDefault(student.getId(), 0),
                            reportCounts.getOrDefault(student.getId(), 0)
                    ))
                    // Administrators first, then newest joined first. The id is the
                    // final tiebreaker so the order is stable across calls even when
                    // two accounts share a joined timestamp.
                    .sorted(Comparator
                            .comparing((UserResponse user) -> user.role() == UserRole.ADMIN ? 0 : 1)
                            .thenComparing(UserResponse::joined, Comparator.reverseOrder())
                            .thenComparing(user -> user.id().toString()))
                    .toList();
            return new UserListResponse("Success", true, users, null);
        }

        List<Student> fetched = studentRepository.findAll(
                specification,
                PageRequest.of(0, limit + 1, KeysetPage.byCreatedAtThenId(direction))
        ).getContent();
        KeysetPage.Slice<Student> slice = KeysetPage.of(fetched, limit,
                last -> cursorCodec.encode(last.getCreatedAt(), last.getId()));
        List<Student> page = slice.items();
        String nextCursor = slice.nextCursor();

        List<UserResponse> users = page.stream()
                .map(student -> mapper.toResponse(
                        student,
                        adminIds.contains(student.getId()),
                        warningCounts.getOrDefault(student.getId(), 0),
                        reportCounts.getOrDefault(student.getId(), 0)
                ))
                .toList();
        return new UserListResponse("Success", true, users, nextCursor);
    }

    /**
     * Returns getStudent.
     *
     * @param studentId the studentId
     * @return the result
     */
    @Transactional(readOnly = true)
    public UserResponse getStudent(UUID studentId) {
        // A soft-deleted account reads; it is anonymised, not absent. This used to exclude
        // them, which made 404 answer two different questions -- "no such account" and "that
        // account was deleted" -- and left the panel unable to tell them apart. 404 now means
        // only the first.
        //
        // Opened together with WarningService.getWarningsForStudent and not before it: the
        // panel's profile modal reads both in one pass and renders the whole profile as gone
        // if either refuses, so one without the other would have changed the contract and
        // fixed nothing. Reading only -- every mutating route still refuses a deleted target
        // through ModeratedStudents.findMutable.
        Student student = studentRepository
                .findById(studentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        return mapper.toResponse(student);
    }
}
