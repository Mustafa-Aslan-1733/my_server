package com.pse.moderation.service.user;

import com.pse.shared.enums.UserRole;
import com.pse.shared.enums.UserStatus;
import com.pse.shared.error.ApiException;
import com.pse.user.model.Student;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

/**
 * How {@code GET /users} turns its query string into a specification.
 *
 * <p>Apart from {@code UserDirectoryService} so that the listing reads as a listing: parsing
 * four string parameters and deciding what a role filter means is not what a directory does,
 * it is what its caller asked for. Package-private, because nothing outside this package has
 * a query string to parse.
 */
final class UserListQuery {

    private UserListQuery() {
    }

    /**
     * Role is membership of the {@code admins} table rather than a column on the student, so
     * it filters as an id set. The empty set still has to produce a valid predicate:
     * {@code IN ()} is not legal SQL, so both degenerate cases are answered directly.
     *
     * @param builder the builder
     * @param root the root
     * @param adminIds the adminIds
     * @param role the role
     * @return the result
     */
    static Predicate rolePredicate(
            CriteriaBuilder builder,
            Root<Student> root,
            Set<UUID> adminIds,
            UserRole role
    ) {
        if (adminIds.isEmpty()) {
            return role == UserRole.ADMIN ? builder.disjunction() : builder.conjunction();
        }
        Predicate isAdmin = root.get("id").in(adminIds);
        return role == UserRole.ADMIN ? isAdmin : builder.not(isAdmin);
    }

    /**
     * Every value of the enum is accepted, {@code DELETED} included. It used to be refused,
     * and the refusal was about the listing rather than about the filter: deleted accounts
     * were excluded from every query unconditionally, so asking for them could only have
     * produced an empty page, and an empty page reads as "there are none" rather than "you
     * cannot ask that". The exclusion is conditional now -- see {@code UserDirectoryService}
     * -- and this filter is what lifts it, so there is nothing left to refuse.
     *
     * @param rawStatus the rawStatus
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    static UserStatus parseStatusFilter(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        try {
            return UserStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid user status");
        }
    }

    static UserRole parseUserRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            return null;
        }
        try {
            return UserRole.valueOf(rawRole.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid user role");
        }
    }

    /**
     * {@code sort=newest} (the default) or {@code sort=oldest}, as the direction the keyset
     * ordering runs in. Resolved to a {@code Sort.Direction} here rather than carried as a
     * {@code newestFirst} boolean: the flag had to be read the same way at the cursor
     * predicate and at the {@code Sort}, and nothing tied the two together.
     *
     * @param rawSort the rawSort
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    static Sort.Direction parseSortDirection(String rawSort) {
        if (rawSort == null || rawSort.isBlank() || rawSort.trim().equalsIgnoreCase("newest")) {
            return Sort.Direction.DESC;
        }
        if (rawSort.trim().equalsIgnoreCase("oldest")) {
            return Sort.Direction.ASC;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid user sort");
    }
}
