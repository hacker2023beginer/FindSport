package by.findsport.web;

import by.findsport.domain.Language;
import by.findsport.service.CatalogService;
import by.findsport.service.command.CatalogCriteria;
import by.findsport.service.command.CatalogSort;
import by.findsport.web.dto.CatalogResponse;
import by.findsport.web.dto.CatalogSearchRequest;
import by.findsport.web.dto.MapMarkerResponse;
import by.findsport.web.error.InvalidParameterException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Публичный каталог Платформы: поиск по мероприятиям, организациям и тренерам,
 * фильтры, сортировка, постраничный вывод и метки карты (ТЗ п. 4.6.2–4.6.4, 4.7, 4.9.4, 4.10.3).
 * Доступен гостю (п. 4.2.1).
 */
@Tag(name = "Каталог (публичный)",
        description = "Объединенный поиск по мероприятиям, организациям и тренерам, метки карты")
@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalog;

    @Operation(summary = "Каталог объектов с фильтрами и сортировкой",
            description = "Регистронезависимый поиск по частичному совпадению; фильтры: область, город, "
                    + "вид спорта, возраст, цена, дата, свободные места; постраничный вывод")
    @GetMapping
    public CatalogResponse search(@Valid @ParameterObject @ModelAttribute CatalogSearchRequest request, Locale locale) {
        Language language = Language.from(locale);
        return catalog.search(toCriteria(request), sort(request.sort()),
                request.pageOrDefault(), request.sizeOrDefault(), language);
    }

    @Operation(summary = "Метки мероприятий для карты",
            description = "Опубликованные мероприятия с координатами населенного пункта адреса")
    @GetMapping("/map")
    public List<MapMarkerResponse> map(@Valid @ParameterObject @ModelAttribute CatalogSearchRequest request,
                                        Locale locale) {
        return catalog.markers(toCriteria(request), Language.from(locale));
    }

    /** Разбор параметров сортировки: неизвестное значение — понятное сообщение об ошибке. */
    private static CatalogSort sort(String value) {
        if (value == null || value.isBlank()) {
            return CatalogSort.NEWEST;
        }
        try {
            return CatalogSort.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new InvalidParameterException("sort", "Неизвестный порядок сортировки: " + value);
        }
    }

    private static CatalogCriteria toCriteria(CatalogSearchRequest request) {
        return new CatalogCriteria(request.q(), request.type() == null ? Set.of() : request.type(),
                request.regionId(), request.city(), request.sportId(), request.format(), request.age(),
                request.priceMin(), request.priceMax(), request.dateFrom(), request.dateTo(),
                Boolean.TRUE.equals(request.hasFreeSeats()), request.providerId());
    }
}
