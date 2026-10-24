package by.findsport.repository;

import by.findsport.domain.EventModeration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventModerationRepository extends JpaRepository<EventModeration, Long> {

    @EntityGraph(attributePaths = {"submittedBy"})
    Optional<EventModeration> findFirstByEventIdAndDecisionIsNull(Long eventId);

    /** Очередь модератора: нерассмотренные отправки — новые карточки и правки опубликованных. */
    @EntityGraph(attributePaths = {"event", "event.provider"})
    Page<EventModeration> findByDecisionIsNull(Pageable pageable);

    /** Последняя отправка по каждой карточке — состояние правок для кабинета автора (одним запросом на страницу). */
    @Query("""
            select m from EventModeration m
            where m.event.id in :eventIds
              and m.id = (select max(m2.id) from EventModeration m2 where m2.event = m.event)
            """)
    List<EventModeration> findLatestByEventIds(@Param("eventIds") Collection<Long> eventIds);
}
