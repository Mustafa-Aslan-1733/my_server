package com.pse.shared.page;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import com.pse.shared.error.ApiException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

/**
 * The mechanics every keyset-paginated listing shares: the limit parameter, the guard that a
 * cursor needs one, the {@code (createdAt, id)} ordering, the predicate that resumes after a
 * cursor, and the one-row-too-many trick that decides whether there is a next page.
 *
 * <p>Three listings implemented all of that separately -- {@code AuditLogService.getLogs},
 * {@code ModerationUserService.getStudents} and {@code ModerationContentService.getRatings}.
 * The parsers were identical down to the message string, and the slice was identical down to
 * the {@code !page.isEmpty()} guard. Three copies of a rule about ordering is three chances
 * for a page to skip or repeat a row, which is the failure this shape exists to avoid.
 *
 * <p>Encoding stays with {@code KeysetCursorCodec} and its per-domain wrappers: the caller
 * passes {@link #of} a function, because {@code AuditLog} keeps its timestamp as an
 * {@code Instant} and {@code Student} and {@code Rating} keep theirs as a
 * {@code LocalDateTime}.
 */
public final class KeysetPage {

    /**
     * The MAX_LIMIT.
     */
    public static final int MAX_LIMIT = 100;

    private static final String LIMIT_MESSAGE =
            "Limit must be an integer between 1 and " + MAX_LIMIT;

    private KeysetPage() {
    }

    /**
     * A page of rows and the cursor that resumes after them, or {@code null} when the page is
     * the last one.
     *
     * @param <T> the type parameter
     * @param items the items
     * @param nextCursor the nextCursor
     */
    public record Slice<T>(List<T> items, String nextCursor) {
    }

    /**
     * The limit for a listing where absent means "do not paginate" -- which is not the same
     * as a default page size, and is why this returns a boxed {@code Integer}.
     *
     * @param rawLimit the rawLimit
     * @return the result
     */
    public static Integer optionalLimit(String rawLimit) {
        if (rawLimit == null || rawLimit.isBlank()) {
            return null;
        }
        return bounded(rawLimit);
    }

    /**
     * The limit for a listing that always paginates, falling back to {@code fallback}.
     *
     * @param rawLimit the rawLimit
     * @param fallback the fallback
     * @return the result
     */
    public static int limitOr(String rawLimit, int fallback) {
        if (rawLimit == null || rawLimit.isBlank()) {
            return fallback;
        }
        return bounded(rawLimit);
    }

    private static int bounded(String rawLimit) {
        int limit;
        try {
            limit = Integer.parseInt(rawLimit.trim());
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, LIMIT_MESSAGE);
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, LIMIT_MESSAGE);
        }
        return limit;
    }

    /**
     * A cursor with no limit has no page size to resume with. Answering it by returning
     * everything from that point would quietly ignore half of what was asked. Call this
     * before decoding the cursor: how the request is formed is the more fundamental
     * complaint than what the cursor happens to contain.
     *
     * @param hasCursor the hasCursor
     * @param limit the limit
     *
     * @throws ApiException if the operation fails
     */
    public static void requireLimitWithCursor(boolean hasCursor, Integer limit) {
        if (hasCursor && limit == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A cursor requires a limit");
        }
    }

    /**
     * The total ordering every cursor in this codebase is defined against.
     *
     * @param direction the direction
     * @return the result
     */
    public static Sort byCreatedAtThenId(Sort.Direction direction) {
        return Sort.by(new Sort.Order(direction, "createdAt"), new Sort.Order(direction, "id"));
    }

    /**
     * Resumes the {@code (createdAt, id)} ordering strictly after the cursor's row. The id is
     * the tie-breaker and is what makes the ordering total -- two rows can share a timestamp,
     * and without it a page boundary that falls between them drops or repeats one.
     *
     * @param <Y> the type parameter
     * @param builder the builder
     * @param root the root
     * @param createdAt the createdAt
     * @param id the id
     * @param direction the direction
     * @return the result
     */
    public static <Y extends Comparable<? super Y>> Predicate after(
            CriteriaBuilder builder,
            Root<?> root,
            Y createdAt,
            UUID id,
            Sort.Direction direction
    ) {
        Path<Y> at = root.get("createdAt");
        Path<UUID> rowId = root.get("id");
        if (direction.isDescending()) {
            return builder.or(
                    builder.lessThan(at, createdAt),
                    builder.and(
                            builder.equal(at, createdAt),
                            builder.lessThan(rowId, id)
                    )
            );
        }
        return builder.or(
                builder.greaterThan(at, createdAt),
                builder.and(
                        builder.equal(at, createdAt),
                        builder.greaterThan(rowId, id)
                )
        );
    }

    /**
     * Cuts a fetch of {@code limit + 1} rows down to the page, and encodes the cursor only
     * when the extra row proved there is more to come.
     *
     * <p>Fetching one row too many is what says there is a next page without a second count
     * query that could disagree with this one.
     *
     * @param <T> the type parameter
     * @param fetched the fetched
     * @param limit the limit
     * @param encodeCursor the encodeCursor
     * @return the result
     */
    public static <T> Slice<T> of(List<T> fetched, int limit, Function<T, String> encodeCursor) {
        boolean hasNext = fetched.size() > limit;
        List<T> page = hasNext ? fetched.subList(0, limit) : fetched;
        String nextCursor = hasNext && !page.isEmpty()
                ? encodeCursor.apply(page.getLast())
                : null;
        return new Slice<>(page, nextCursor);
    }
}
