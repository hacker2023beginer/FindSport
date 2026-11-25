package by.findsport.service;

import by.findsport.domain.CatalogObjectType;
import by.findsport.domain.Language;
import by.findsport.domain.Sport;
import by.findsport.repository.CatalogRepository;
import by.findsport.repository.SportRepository;
import by.findsport.service.command.CatalogCriteria;
import by.findsport.service.command.CatalogSort;
import by.findsport.web.dto.CatalogItemResponse;
import by.findsport.web.dto.DictionaryItemResponse;
import by.findsport.web.dto.HomeResponse;
import by.findsport.web.dto.PopularSportResponse;
import by.findsport.web.mapper.ApiMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Главная страница Платформы: ближайшие мероприятия, виды спорта с числом мероприятий
 * и регионы для быстрого выбора (ТЗ п. 4.6.3).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    private static final int NEAREST_EVENTS = 6;
    private static final int POPULAR_SPORTS = 8;

    private final CatalogService catalog;
    private final CatalogRepository catalogRepository;
    private final SportRepository sports;
    private final DictionaryService dictionaries;
    private final Clock clock;

    public HomeResponse home(Language language) {
        CatalogCriteria eventsOnly = new CatalogCriteria(null, Set.of(CatalogObjectType.EVENT),
                null, null, null, null, null, null, null, null, null, false);
        List<CatalogItemResponse> nearest = catalog.search(eventsOnly, CatalogSort.DATE, 0, NEAREST_EVENTS, language)
                .content();
        Map<String, Long> counts = catalogRepository.countBySport(eventsOnly, OffsetDateTime.now(clock));
        List<PopularSportResponse> popular = popularSports(counts, language);
        List<DictionaryItemResponse> regions = dictionaries.regions().stream()
                .map(region -> ApiMapper.region(region, language))
                .toList();
        return new HomeResponse(nearest, popular, regions);
    }

    /** Виды спорта, отсортированные по числу мероприятий: сначала самые популярные. */
    private List<PopularSportResponse> popularSports(Map<String, Long> counts, Language language) {
        List<Sport> all = sports.findAll();
        List<PopularSportResponse> result = new ArrayList<>(all.size());
        for (Sport sport : all) {
            long count = counts.getOrDefault(sport.getCode(), 0L);
            result.add(new PopularSportResponse(sport.getId(), sport.getCode(),
                    sport.getName().get(language), count));
        }
        result.sort(Comparator.comparingLong(PopularSportResponse::eventCount).reversed()
                .thenComparing(PopularSportResponse::name));
        return result.size() > POPULAR_SPORTS ? result.subList(0, POPULAR_SPORTS) : result;
    }
}
