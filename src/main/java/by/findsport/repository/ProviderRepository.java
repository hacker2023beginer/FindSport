package by.findsport.repository;

import by.findsport.domain.Provider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

    @EntityGraph(attributePaths = {"region", "sports"})
    List<Provider> findByOwnerIdOrderByIdDesc(Long ownerId);

    @EntityGraph(attributePaths = {"owner", "region", "sports"})
    Optional<Provider> findWithDetailsById(Long id);
}
