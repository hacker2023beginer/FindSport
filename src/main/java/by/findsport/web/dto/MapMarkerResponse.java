package by.findsport.web.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Метка мероприятия на карте (ТЗ п. 4.6.2). Координаты определены по населенному пункту
 * адреса карточки; переход по метке открывает карточку мероприятия.
 */
public record MapMarkerResponse(
        Long id,
        String title,
        OffsetDateTime startsAt,
        String locality,
        String region,
        BigDecimal latitude,
        BigDecimal longitude) {
}
