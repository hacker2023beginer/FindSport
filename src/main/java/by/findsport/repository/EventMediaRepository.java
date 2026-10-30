package by.findsport.repository;

import by.findsport.domain.EventMedia;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventMediaRepository extends JpaRepository<EventMedia, Long> {

    List<EventMedia> findByEventIdOrderByIdAsc(Long eventId);
}
