package by.findsport.repository;

import by.findsport.domain.EventMedia;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventMediaRepository extends JpaRepository<EventMedia, Long> {

    /** Все материалы карточки, включая ожидающие модерации, — для автора и модератора. */
    List<EventMedia> findByEventIdOrderByIdAsc(Long eventId);

    /** Публичная версия: только одобренные материалы (п. 4.14.1). */
    List<EventMedia> findByEventIdAndPublishedTrueOrderByIdAsc(Long eventId);

    Optional<EventMedia> findByIdAndEventId(Long id, Long eventId);
}
