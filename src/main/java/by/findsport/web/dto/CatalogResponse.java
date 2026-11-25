package by.findsport.web.dto;

import by.findsport.domain.CatalogObjectType;
import java.util.List;
import java.util.Map;

/**
 * Страница публичного каталога (ТЗ п. 4.7.7–4.7.8): общее число найденного, постраничная
 * навигация и счетчики для панели фильтров.
 *
 * @param countsByType число объектов каждого типа при текущих фильтрах
 * @param countsBySport число объектов по каждому виду спорта при текущих фильтрах
 */
public record CatalogResponse(
        List<CatalogItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        Map<CatalogObjectType, Long> countsByType,
        Map<String, Long> countsBySport) {
}
