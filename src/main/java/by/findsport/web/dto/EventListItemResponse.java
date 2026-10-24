package by.findsport.web.dto;

import by.findsport.domain.EventType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Элемент списка мероприятий (ТЗ п. 4.7.6, 4.9.2): название, вид спорта, дата и время, место,
 * организатор, краткое описание и ключевые характеристики. Изображение-заглушку подставляет клиент.
 */
public record EventListItemResponse(
        Long id,
        EventType type,
        String title,
        String shortDescription,
        List<String> sports,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        PublicAddressResponse address,
        String organizer,
        BigDecimal minPrice,
        String currency,
        Integer ageMin,
        Integer ageMax) {
}
