package by.findsport.service.command;

import by.findsport.domain.CatalogObjectType;
import by.findsport.domain.EventType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

/**
 * Параметры каталога — объединенного поиска по мероприятиям, организациям и тренерам
 * (ТЗ п. 4.6.2, 4.6.4, 4.7.1–4.7.2, 4.9.4, 4.10.3).
 * <p>
 * Фильтры по возрасту, цене, дате, формату и свободным местам применимы только к мероприятиям:
 * для организаций и тренеров таких сведений карточка не содержит (п. 4.10.4, 5.2).
 *
 * @param types типы объектов, попадающих в выдачу; пусто — все типы
 */
public record CatalogCriteria(
        String query,
        Set<CatalogObjectType> types,
        Long regionId,
        String city,
        Long sportId,
        EventType eventType,
        Integer age,
        BigDecimal priceMin,
        BigDecimal priceMax,
        LocalDate dateFrom,
        LocalDate dateTo,
        boolean onlyWithFreeSeats) {

    /**
     * Признак того, что выдача ограничена мероприятиями из-за фильтра, недоступного поставщикам.
     * Фильтры возраста, цены, даты, формата и свободных мест к профилю поставщика неприменимы
     * (ТЗ п. 4.10.4, 5.2), поэтому при их применении карточки поставщиков из выдачи исключаются.
     */
    public boolean eventsOnly() {
        return eventType != null || age != null || priceMin != null || priceMax != null
                || dateFrom != null || dateTo != null || onlyWithFreeSeats;
    }
}
