package by.findsport.repository;

import by.findsport.domain.Booking;
import by.findsport.domain.BookingStatus;
import by.findsport.domain.CardStatus;
import by.findsport.domain.Event;
import by.findsport.domain.EventOffer;
import by.findsport.domain.EventType;
import by.findsport.domain.Sport;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Условия публичного поиска мероприятий (ТЗ п. 4.7, 4.9.4–4.9.5).
 * Текстовые условия строятся как {@code lower(col) LIKE '%q%'} — под них созданы GIN trigram-индексы.
 */
public final class EventSpecifications {

    /** Заявки, занимающие место (п. 4.9.7). */
    public static final List<BookingStatus> SEAT_TAKING_STATUSES = List.of(BookingStatus.SUBMITTED, BookingStatus.CONFIRMED);

    private static final char LIKE_ESCAPE = '\\';

    private EventSpecifications() {
    }

    public static Specification<Event> published() {
        return (root, query, cb) -> cb.equal(root.get("status"), CardStatus.PUBLISHED);
    }

    /** Завершенные мероприятия исключаются из предстоящих (п. 4.9.5). */
    public static Specification<Event> notFinishedAt(OffsetDateTime moment) {
        return (root, query, cb) -> {
            Expression<OffsetDateTime> end = cb.coalesce(root.get("endsAt"), root.get("startsAt"));
            return cb.greaterThanOrEqualTo(end, moment);
        };
    }

    /** Наименования, описания, виды спорта, населенные пункты и адреса (п. 4.7.1), без учета регистра (п. 4.7.4). */
    public static Specification<Event> textMatches(String text) {
        String pattern = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> {
            Subquery<Long> sportMatch = query.subquery(Long.class);
            Root<Event> correlated = sportMatch.correlate(root);
            Join<Event, Sport> sport = correlated.join("sports");
            sportMatch.select(sport.get("id")).where(cb.or(
                    like(cb, sport.get("name").get("ru"), pattern),
                    like(cb, sport.get("name").get("en"), pattern)));

            return cb.or(
                    like(cb, root.get("title").get("ru"), pattern),
                    like(cb, root.get("title").get("en"), pattern),
                    like(cb, root.get("description").get("ru"), pattern),
                    like(cb, root.get("description").get("en"), pattern),
                    like(cb, root.get("address").get("locality"), pattern),
                    like(cb, root.get("address").get("street"), pattern),
                    like(cb, root.get("address").get("district"), pattern),
                    cb.exists(sportMatch));
        };
    }

    public static Specification<Event> inRegion(Long regionId) {
        return (root, query, cb) -> cb.equal(root.get("region").get("id"), regionId);
    }

    public static Specification<Event> inLocality(String locality) {
        return (root, query, cb) -> cb.equal(cb.lower(root.get("address").get("locality")),
                locality.trim().toLowerCase(Locale.ROOT));
    }

    public static Specification<Event> hasSport(Long sportId) {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Join<Event, Sport> sport = sq.correlate(root).join("sports");
            sq.select(sport.get("id")).where(cb.equal(sport.get("id"), sportId));
            return cb.exists(sq);
        };
    }

    public static Specification<Event> ofType(EventType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    /** Возрастной ценз допускает указанный возраст. */
    public static Specification<Event> suitableForAge(int age) {
        return (root, query, cb) -> cb.and(
                cb.or(cb.isNull(root.get("ageMin")), cb.lessThanOrEqualTo(root.get("ageMin"), age)),
                cb.or(cb.isNull(root.get("ageMax")), cb.greaterThanOrEqualTo(root.get("ageMax"), age)));
    }

    /** Есть хотя бы одно предложение в диапазоне цен. */
    public static Specification<Event> priceBetween(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            Subquery<Long> sq = query.subquery(Long.class);
            Root<EventOffer> offer = sq.from(EventOffer.class);
            sq.select(offer.get("id")).where(
                    cb.equal(offer.get("event"), root),
                    min != null ? cb.greaterThanOrEqualTo(offer.get("price"), min) : cb.conjunction(),
                    max != null ? cb.lessThanOrEqualTo(offer.get("price"), max) : cb.conjunction());
            return cb.exists(sq);
        };
    }

    public static Specification<Event> startsBetween(OffsetDateTime from, OffsetDateTime toExclusive) {
        return (root, query, cb) -> cb.and(
                from != null ? cb.greaterThanOrEqualTo(root.get("startsAt"), from) : cb.conjunction(),
                toExclusive != null ? cb.lessThan(root.get("startsAt"), toExclusive) : cb.conjunction());
    }

    /** Наличие свободных мест (п. 4.7.2): лимит не задан или занятых мест меньше лимита. */
    public static Specification<Event> hasFreeSeats() {
        return (root, query, cb) -> {
            Subquery<Long> taken = query.subquery(Long.class);
            Root<Booking> booking = taken.from(Booking.class);
            taken.select(cb.count(booking)).where(
                    cb.equal(booking.get("event"), root),
                    booking.get("status").in(SEAT_TAKING_STATUSES));
            return cb.or(
                    cb.isNull(root.get("participantLimit")),
                    cb.greaterThan(root.<Integer>get("participantLimit").as(Long.class), taken));
        };
    }

    private static Predicate like(CriteriaBuilder cb, Expression<String> column, String pattern) {
        return cb.like(cb.lower(column), pattern, LIKE_ESCAPE);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
