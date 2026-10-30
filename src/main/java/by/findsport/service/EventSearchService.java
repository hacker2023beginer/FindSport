package by.findsport.service;

import static by.findsport.repository.EventSpecifications.SEAT_TAKING_STATUSES;

import by.findsport.domain.BaseEntity;
import by.findsport.domain.CardStatus;
import by.findsport.domain.Event;
import by.findsport.repository.BookingRepository;
import by.findsport.repository.EventRepository;
import by.findsport.repository.EventSpecifications;
import by.findsport.service.command.EventSearchCriteria;
import by.findsport.service.exception.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Публичный поиск и просмотр опубликованных мероприятий (ТЗ п. 4.2.1, 4.7, 4.9).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventSearchService {

    /** Сортировка по новизне (п. 4.7.3). */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));

    private final EventRepository events;
    private final BookingRepository bookings;
    private final Clock clock;

    public Page<Event> search(EventSearchCriteria criteria, int page, int size) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Specification<Event> spec = EventSpecifications.published().and(EventSpecifications.notFinishedAt(now));
        if (StringUtils.hasText(criteria.query())) {
            spec = spec.and(EventSpecifications.textMatches(criteria.query()));
        }
        if (criteria.regionId() != null) {
            spec = spec.and(EventSpecifications.inRegion(criteria.regionId()));
        }
        if (StringUtils.hasText(criteria.city())) {
            spec = spec.and(EventSpecifications.inLocality(criteria.city()));
        }
        if (criteria.sportId() != null) {
            spec = spec.and(EventSpecifications.hasSport(criteria.sportId()));
        }
        if (criteria.type() != null) {
            spec = spec.and(EventSpecifications.ofType(criteria.type()));
        }
        if (criteria.age() != null) {
            spec = spec.and(EventSpecifications.suitableForAge(criteria.age()));
        }
        if (criteria.priceMin() != null || criteria.priceMax() != null) {
            spec = spec.and(EventSpecifications.priceBetween(criteria.priceMin(), criteria.priceMax()));
        }
        if (criteria.dateFrom() != null || criteria.dateTo() != null) {
            spec = spec.and(EventSpecifications.startsBetween(startOfDay(criteria.dateFrom()),
                    criteria.dateTo() != null ? startOfDay(criteria.dateTo().plusDays(1)) : null));
        }
        if (criteria.onlyWithFreeSeats()) {
            spec = spec.and(EventSpecifications.hasFreeSeats());
        }

        Page<Event> found = events.findAll(spec, PageRequest.of(page, size, NEWEST_FIRST));
        if (found.isEmpty()) {
            return found;
        }
        Map<Long, Event> detailed = events.findWithDetailsByIdIn(found.map(BaseEntity::getId).getContent()).stream()
                .collect(Collectors.toMap(BaseEntity::getId, Function.identity()));
        return found.map(event -> detailed.get(event.getId()));
    }

    public PublicEventCard getPublished(Long id) {
        Event event = events.findWithDetailsById(id)
                .filter(e -> e.getStatus() == CardStatus.PUBLISHED)
                .orElseThrow(() -> new NotFoundException("Мероприятие не найдено"));
        long taken = bookings.countByEventIdAndStatusIn(id, SEAT_TAKING_STATUSES);
        Integer freeSeats = event.getParticipantLimit() == null
                ? null
                : (int) Math.max(0, event.getParticipantLimit() - taken);
        return new PublicEventCard(event, freeSeats, event.isRegistrationOpenAt(OffsetDateTime.now(clock), taken));
    }

    private OffsetDateTime startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(clock.getZone()).toOffsetDateTime();
    }
}
