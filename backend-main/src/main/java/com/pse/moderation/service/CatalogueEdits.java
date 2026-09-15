package com.pse.moderation.service;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import com.pse.shared.error.ApiException;
import org.springframework.http.HttpStatus;

/**
 * The bits an edit to a lecture and an edit to a professor do the same way: validate a name,
 * resolve a list of ids to rows, and decide whether an assignment actually changed.
 *
 * <p>{@link #resolveAll} is one method where there were two -- {@code resolveProfessors} and
 * {@code resolveLectures} were the same body with the type swapped, down to the shape of the
 * count comparison that catches an unknown id.
 */
final class CatalogueEdits {

    private static final int MAX_NAME_LENGTH = 200;

    private CatalogueEdits() {
    }

    /**
     * Every row named by {@code ids}, or a 404 naming what was missing.
     *
     * <p>The comparison is against the number of <em>distinct</em> ids: a request repeating an
     * id is not a request for a row that does not exist, and must not be answered as one.
     *
     * @param <T> the type parameter
     * @param ids the ids
     * @param findAllById the findAllById
     * @param missingMessage the missingMessage
     * @return the result
     *
     * @throws ApiException if the operation fails
     */
    static <T> List<T> resolveAll(List<UUID> ids, Function<List<UUID>, List<T>> findAllById,
                                  String missingMessage) {
        List<T> found = findAllById.apply(ids);
        if (found.size() != ids.stream().distinct().count()) {
            throw new ApiException(HttpStatus.NOT_FOUND, missingMessage);
        }
        return found;
    }

    static String requireName(String raw, String message) {
        String name = raw.trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
        return name;
    }

    /**
     * The ids as strings, in a stable order.
     *
     * <p>Sorted, and that is the whole point. One side of every assignment is read from a
     * {@code Set} whose iteration order is the JVM's business -- {@code Professor} and
     * {@code Lecture} override neither {@code equals} nor {@code hashCode}, so it can differ
     * per run. This value is written into the audit entry's {@code changes}, stored in a
     * {@code jsonb} column that preserves array order, and served by
     * {@code GET /admin/audit-logs}, so an unsorted list means two servers can answer the
     * same request with the professor list in different orders. F-21 on the audit record.
     *
     * <p>{@link #sameAssignment} uses this too, so the order a change is *decided* by and the
     * order it is *recorded* in are the same one rather than two that have to be kept in step.
     */
    static List<String> labels(List<UUID> ids) {
        return ids.stream().map(UUID::toString).sorted().toList();
    }

    /**
     * Whether two id assignments hold the same members, regardless of the order they come
     * out in. Both sides are read from a {@code Set} on one end and from a request or a
     * repository lookup on the other, so position carries no information -- BUG-3.
     *
     * @param before the before
     * @param after the after
     * @return the result
     */
    static boolean sameAssignment(List<UUID> before, List<UUID> after) {
        return labels(before).equals(labels(after));
    }
}
