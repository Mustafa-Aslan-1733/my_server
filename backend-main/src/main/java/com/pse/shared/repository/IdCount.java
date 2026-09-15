package com.pse.shared.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One row of a {@code GROUP BY} count: the id grouped on, and how many rows fell under it.
 *
 * <p>A Spring Data interface projection rather than the {@code List<Object[]>} these five
 * queries used to return. The old shape pushed two unchecked casts --
 * {@code (UUID) row[0]} and {@code ((Number) row[1]).intValue()} -- onto every caller, and
 * the loop that performed them was byte-identical in {@code ModerationUserService} and
 * {@code ModerationContentService}. Column order was the only thing keeping the casts
 * correct, and nothing checked it.
 *
 * <p>The query aliases must stay {@code id} and {@code count}: that is what binds them to
 * the getters below.
 */
public interface IdCount {

    /**
     * Simple Getter.
     * @return the id
     */
    UUID getId();

    /**
     * Simple Getter.
     * @return the count
     */
    long getCount();

    /**
     * Collapses the rows into the lookup the callers actually want. A {@code HashMap} and a
     * loop rather than {@code Collectors.toMap}, which throws on a null key -- a grouped
     * column is not guaranteed non-null and the previous code tolerated one.
     *
     * @param rows the rows
     * @return the result
     */
    static Map<UUID, Integer> asMap(List<IdCount> rows) {
        Map<UUID, Integer> counts = new HashMap<>();
        for (IdCount row : rows) {
            counts.put(row.getId(), (int) row.getCount());
        }
        return counts;
    }
}
