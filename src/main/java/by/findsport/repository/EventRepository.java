package by.findsport.repository;

import by.findsport.domain.CardStatus;
import by.findsport.domain.Event;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Пагинация выполняется по «плоскому» запросу, а коллекции догружаются вторым запросом
 * {@link #findWithDetailsByIdIn}: fetch join коллекций вместе с LIMIT привел бы к пагинации в памяти,
 * а ленивая загрузка в цикле — к N+1.
 */
public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

    @EntityGraph(attributePaths = {"provider", "region", "sports", "offers"})
    List<Event> findWithDetailsByIdIn(Collection<Long> ids);

    @EntityGraph(attributePaths = {"provider", "provider.owner", "region", "sports", "offers"})
    Optional<Event> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"provider"})
    Page<Event> findByStatus(CardStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"provider"})
    Page<Event> findByProviderOwnerId(Long ownerId, Pageable pageable);
}
