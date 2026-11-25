package by.findsport.repository;

import by.findsport.domain.Locality;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalityRepository extends JpaRepository<Locality, Long> {

    /** Населенный пункт по названию без учета регистра — координаты метки карты (ТЗ п. 4.6.2). */
    Optional<Locality> findFirstByNameRuIgnoreCase(String nameRu);

    /** Населенные пункты области — подсказки фильтра «Город» (п. 4.7.2). */
    List<Locality> findByRegionCodeOrderByNameRu(String regionCode);

    List<Locality> findByRegionCodeInOrderByNameRu(Collection<String> regionCodes);
}
