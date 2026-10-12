package com.travelops.repository;

import com.travelops.entity.Post;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/**
 * Builds the WHERE clause only from filters the caller actually sent, so an absent
 * filter adds no predicate instead of an "or :param is null" branch.
 */
public final class PostSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private PostSpecifications() {
    }

    public static Specification<Post> destinationEquals(String destinationCity) {
        return (root, query, cb) -> cb.equal(root.get("destinationCity"), destinationCity);
    }

    public static Specification<Post> keywordMatches(String keyword) {
        String pattern = "%" + escapeLike(keyword.toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("content")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("destinationCity")), pattern, LIKE_ESCAPE));
    }

    // A user searching for "50%" should match the literal text, not "50 followed by anything".
    static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
