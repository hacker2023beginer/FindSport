package by.findsport.repository;

import by.findsport.domain.CardStatus;
import by.findsport.domain.Provider;
import by.findsport.domain.ProviderType;
import by.findsport.domain.Sport;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Условия публичного поиска по организациям и тренерам (ТЗ п. 4.7.1, 4.7.4, 4.10.3).
 * Текстовые условия строятся как {@code lower(col) LIKE '%q%'} — под них созданы GIN trigram-индексы.
 * <p>
 * Публичная выдача содержит только подтвержденные модератором профили (п. 4.10.5).
 */
public final class ProviderSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private ProviderSpecifications() {
    }

    /** Подтвержденный модератором профиль (п. 4.10.5). Черновики и архивные записи не выдаются. */
    public static Specification<Provider> published() {
        return (root, query, cb) -> cb.equal(root.get("status"), CardStatus.PUBLISHED);
    }

    public static Specification<Provider> ofType(ProviderType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Provider> inRegion(Long regionId) {
        return (root, query, cb) -> cb.equal(root.get("region").get("id"), regionId);
    }

    /** Город — точное совпадение без учета регистра (п. 4.7.2). */
    public static Specification<Provider> inLocality(String locality) {
        return (root, query, cb) -> cb.equal(cb.lower(root.get("address").get("locality")),
                locality.trim().toLowerCase(Locale.ROOT));
    }

    public static Specification<Provider> hasSport(Long sportId) {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Join<Provider, Sport> sport = sq.correlate(root).join("sports");
            sq.select(sport.get("id")).where(cb.equal(sport.get("id"), sportId));
            return cb.exists(sq);
        };
    }

    /** Наименования, описания, виды спорта, населенные пункты и адреса (п. 4.7.1). */
    public static Specification<Provider> textMatches(String text) {
        String pattern = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> {
            Subquery<Long> sportMatch = query.subquery(Long.class);
            Root<Provider> correlated = sportMatch.correlate(root);
            Join<Provider, Sport> sport = correlated.join("sports");
            sportMatch.select(sport.get("id")).where(cb.or(
                    like(cb, sport.get("name").get("ru"), pattern),
                    like(cb, sport.get("name").get("en"), pattern)));

            return cb.or(
                    like(cb, root.get("name").get("ru"), pattern),
                    like(cb, root.get("name").get("en"), pattern),
                    like(cb, root.get("description").get("ru"), pattern),
                    like(cb, root.get("description").get("en"), pattern),
                    like(cb, root.get("address").get("locality"), pattern),
                    like(cb, root.get("address").get("street"), pattern),
                    like(cb, root.get("address").get("district"), pattern),
                    cb.exists(sportMatch));
        };
    }

    private static Predicate like(CriteriaBuilder cb, Expression<String> column, String pattern) {
        return cb.like(cb.lower(column), pattern, LIKE_ESCAPE);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
