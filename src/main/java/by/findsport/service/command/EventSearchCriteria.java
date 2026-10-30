package by.findsport.service.command;

import by.findsport.domain.EventType;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Параметры публичного поиска (ТЗ п. 4.7.1–4.7.2, 4.9.4); {@code null} — фильтр не применяется. */
public record EventSearchCriteria(
        String query,
        Long regionId,
        String city,
        Long sportId,
        EventType type,
        Integer age,
        BigDecimal priceMin,
        BigDecimal priceMax,
        LocalDate dateFrom,
        LocalDate dateTo,
        boolean onlyWithFreeSeats) {
}
