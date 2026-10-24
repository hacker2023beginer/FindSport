package by.findsport.service;

import by.findsport.domain.Region;
import by.findsport.domain.Sport;
import by.findsport.repository.RegionRepository;
import by.findsport.repository.SportRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Справочники для фильтров поиска и форм (ТЗ п. 4.5.3, 4.6.3). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DictionaryService {

    private static final Sort BY_NAME = Sort.by("name.ru");

    private final RegionRepository regions;
    private final SportRepository sports;

    public List<Region> regions() {
        return regions.findAll(BY_NAME);
    }

    public List<Sport> sports() {
        return sports.findAll(BY_NAME);
    }
}
