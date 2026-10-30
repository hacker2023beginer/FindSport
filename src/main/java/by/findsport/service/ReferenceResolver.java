package by.findsport.service;

import by.findsport.domain.Region;
import by.findsport.domain.Sport;
import by.findsport.repository.RegionRepository;
import by.findsport.repository.SportRepository;
import by.findsport.service.exception.InvalidReferenceException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Проверка ссылок на справочники, присланных клиентом. */
@Component
@RequiredArgsConstructor
class ReferenceResolver {

    private final RegionRepository regions;
    private final SportRepository sports;

    Region region(Long id) {
        return regions.findById(id)
                .orElseThrow(() -> new InvalidReferenceException("regionId", "Регион не найден: " + id));
    }

    Set<Sport> sports(Set<Long> ids) {
        List<Sport> found = sports.findAllById(ids);
        if (found.size() != ids.size()) {
            throw new InvalidReferenceException("sportIds", "Указан несуществующий вид спорта");
        }
        return new LinkedHashSet<>(found);
    }
}
