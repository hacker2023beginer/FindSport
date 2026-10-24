package by.findsport.repository;

import by.findsport.domain.Booking;
import by.findsport.domain.BookingStatus;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** Количество занятых мест для проверки лимита участников (п. 4.9.7). */
    long countByEventIdAndStatusIn(Long eventId, Collection<BookingStatus> statuses);

    /** История заявок пользователя (п. 4.2.5). */
    @EntityGraph(attributePaths = {"event", "offer"})
    Page<Booking> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Поступившие заявки на мероприятие для обработки поставщиком (п. 4.2.10). */
    @EntityGraph(attributePaths = {"user", "offer"})
    Page<Booking> findByEventIdOrderByCreatedAtDesc(Long eventId, Pageable pageable);
}
