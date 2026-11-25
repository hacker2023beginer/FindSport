package by.findsport.web.dto;

import by.findsport.domain.CatalogObjectType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Элемент публичного каталога — мероприятие, организация или тренер (ТЗ п. 4.6.4, 4.7.6).
 * Набор заполненных полей зависит от типа объекта: у мероприятия есть дата, цена и организатор,
 * у поставщика — только сведения профиля (п. 4.10.4).
 */
public record CatalogItemResponse(
        CatalogObjectType objectType,
        Long id,
        String title,
        String description,
        List<String> sports,
        String address,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String format,
        String organizer,
        BigDecimal minPrice,
        String currency,
        Integer ageMin,
        Integer ageMax,
        Integer freeSeats,
        Integer participantLimit,
        String imageUrl,
        OffsetDateTime publishedAt) {
}
