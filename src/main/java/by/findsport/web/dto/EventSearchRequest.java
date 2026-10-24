package by.findsport.web.dto;

import by.findsport.domain.EventType;
import by.findsport.service.command.EventSearchCriteria;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Query-параметры поиска — отражаются в URL (ТЗ п. 4.7.9).
 * Фильтры по п. 4.7.2 и 4.9.4. Фильтр «формат» из п. 4.9.4 — это {@code type}, тип мероприятия
 * (турнир, забег и т. д., решение PM). Цены — в BYN.
 */
public record EventSearchRequest(
        @Size(max = 100) String q,
        @Positive Long regionId,
        @Size(max = 100) String city,
        @Positive Long sportId,
        EventType type,
        @Min(0) @Max(120) Integer age,
        @DecimalMin("0") BigDecimal priceMin,
        @DecimalMin("0") BigDecimal priceMax,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        Boolean hasFreeSeats,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size) {

    public static final int DEFAULT_PAGE_SIZE = 20;

    @AssertTrue(message = "Минимальная цена не может превышать максимальную")
    public boolean isPriceRangeValid() {
        return priceMin == null || priceMax == null || priceMin.compareTo(priceMax) <= 0;
    }

    @AssertTrue(message = "Начальная дата не может быть позже конечной")
    public boolean isDateRangeValid() {
        return dateFrom == null || dateTo == null || !dateFrom.isAfter(dateTo);
    }

    public EventSearchCriteria toCriteria() {
        return new EventSearchCriteria(q, regionId, city, sportId, type, age, priceMin, priceMax,
                dateFrom, dateTo, Boolean.TRUE.equals(hasFreeSeats));
    }

    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? DEFAULT_PAGE_SIZE : size;
    }
}
