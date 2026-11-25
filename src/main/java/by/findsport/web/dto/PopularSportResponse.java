package by.findsport.web.dto;

/**
 * Вид спорта с числом опубликованных мероприятий — быстрый выбор на главной странице
 * (ТЗ п. 4.6.3) и счетчик панели фильтров каталога (п. 4.7.7).
 */
public record PopularSportResponse(Long id, String code, String name, long eventCount) {
}
