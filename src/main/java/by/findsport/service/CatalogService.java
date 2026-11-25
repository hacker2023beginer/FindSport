package by.findsport.service;

import by.findsport.domain.CatalogObjectType;
import by.findsport.domain.Language;
import by.findsport.repository.CatalogRepository;
import by.findsport.repository.CatalogRepository.CatalogPage;
import by.findsport.repository.CatalogRepository.CatalogRow;
import by.findsport.service.command.CatalogCriteria;
import by.findsport.service.command.CatalogSort;
import by.findsport.web.dto.CatalogItemResponse;
import by.findsport.web.dto.CatalogResponse;
import by.findsport.web.dto.MapMarkerResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Публичный каталог Платформы: поиск по мероприятиям, организациям и тренерам
 * с фильтрами, сортировкой и постраничным выводом (ТЗ п. 4.6.2–4.6.4, 4.7, 4.9.4, 4.10.3).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {

    private final CatalogRepository catalog;
    private final Clock clock;

    /**
     * Страница каталога с общим числом найденного и счетчиками для панели фильтров
     * (ТЗ п. 4.7.7 — общая численность найденного и примененные фильтры).
     */
    public CatalogResponse search(CatalogCriteria criteria, CatalogSort sort, int page, int size, Language language) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        CatalogPage found = catalog.findPage(criteria, sort, page, size, now);
        List<CatalogItemResponse> content = found.rows().stream()
                .map(row -> item(row, language))
                .toList();
        Map<CatalogObjectType, Long> byType = catalog.countByType(criteria, now);
        Map<String, Long> bySport = catalog.countBySport(criteria, now);
        return new CatalogResponse(content, page, size, found.totalElements(),
                totalPages(found.totalElements(), size), byType, bySport);
    }

    /** Метки карты: опубликованные мероприятия с координатами (ТЗ п. 4.6.2). */
    public List<MapMarkerResponse> markers(CatalogCriteria criteria, Language language) {
        return catalog.findMarkers(criteria, OffsetDateTime.now(clock)).stream()
                .map(marker -> new MapMarkerResponse(marker.id(),
                        text(marker.titleRu(), marker.titleEn(), language),
                        marker.startsAt(),
                        marker.locality(),
                        text(marker.regionRu(), marker.regionEn(), language),
                        marker.latitude(),
                        marker.longitude()))
                .toList();
    }

    private CatalogItemResponse item(CatalogRow row, Language language) {
        boolean event = row.type() == CatalogObjectType.EVENT;
        List<String> sports = names(row, language);
        String address = String.join(", ", Stream.of(
                text(row.regionRu(), row.regionEn(), language),
                row.district(),
                row.locality(),
                row.street(),
                row.building()).filter(part -> part != null && !part.isBlank()).toList());
        Integer freeSeats = row.participantLimit() == null
                ? null
                : (int) Math.max(0, row.participantLimit() - row.takenSeats());
        BigDecimal price = row.price();
        return new CatalogItemResponse(
                row.type(),
                row.id(),
                text(row.titleRu(), row.titleEn(), language),
                abbreviate(text(row.descriptionRu(), row.descriptionEn(), language)),
                sports,
                address,
                row.startsAt(),
                row.endsAt(),
                event ? row.format() : null,
                event ? text(row.organizerRu(), row.organizerEn(), language) : null,
                price,
                price == null ? null : by.findsport.domain.EventOffer.CURRENCY,
                row.ageMin(),
                row.ageMax(),
                freeSeats,
                row.participantLimit(),
                row.imageMediaId() == null ? null
                        : "/api/v1/events/%d/media/%d".formatted(row.id(), row.imageMediaId()),
                row.publishedAt());
    }

    /** Названия видов спорта на языке ответа; без перевода используется русский (п. 5.8). */
    private static List<String> names(CatalogRow row, Language language) {
        List<String> names = language == Language.EN && !row.sportNamesEn().isEmpty()
                ? row.sportNamesEn()
                : row.sportNamesRu();
        List<String> result = new ArrayList<>(names.size());
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                result.add(name);
            }
        }
        return result;
    }

    /** Текст на языке ответа; при отсутствии перевода используется русский (п. 5.8). */
    private static String text(String ru, String en, Language language) {
        if (language == Language.EN && en != null && !en.isBlank()) {
            return en;
        }
        return ru;
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= 300 ? text : text.substring(0, 299) + "…";
    }

    private static int totalPages(long total, int size) {
        return (int) Math.ceil((double) total / size);
    }
}
