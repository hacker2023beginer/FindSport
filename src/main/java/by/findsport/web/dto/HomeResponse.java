package by.findsport.web.dto;

import java.util.List;

/**
 * Данные главной страницы (ТЗ п. 4.6.3): строка поиска, быстрый выбор вида спорта и региона,
 * ближайшие мероприятия и основные информационные блоки.
 *
 * @param nearestEvents ближайшие предстоящие мероприятия
 * @param popularSports виды спорта с числом опубликованных мероприятий
 */
public record HomeResponse(
        List<CatalogItemResponse> nearestEvents,
        List<PopularSportResponse> popularSports,
        List<DictionaryItemResponse> regions) {
}
