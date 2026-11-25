package by.findsport.web.dto;

import by.findsport.domain.CatalogObjectType;
import by.findsport.domain.EventType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Параметры каталога из URL (ТЗ п. 4.7.9 — параметры поиска отражаются в URL).
 * Фильтры соответствуют требованиям п. 4.7.2 (область, город, вид спорта, возраст, цена, дата,
 * наличие свободных мест) и п. 4.9.4 (вид спорта, регион, дата, формат, возраст, стоимость).
 */
public record CatalogSearchRequest(
        @Size(max = 100) String q,
        Set<CatalogObjectType> type,
        @Positive Long regionId,
        @Size(max = 100) String city,
        @Positive Long sportId,
        EventType format,
        @Min(0) @Max(120) Integer age,
        @DecimalMin("0") BigDecimal priceMin,
        @DecimalMin("0") BigDecimal priceMax,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        Boolean hasFreeSeats,
        String sort,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size,
        @Positive Long providerId) {

    public static final int DEFAULT_PAGE_SIZE = 20;

    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? DEFAULT_PAGE_SIZE : size;
    }
}
