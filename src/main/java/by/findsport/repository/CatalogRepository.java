package by.findsport.repository;

import by.findsport.domain.CatalogObjectType;
import by.findsport.service.command.CatalogCriteria;
import by.findsport.service.command.CatalogSort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Repository;

/**
 * Чтение публичного каталога: мероприятия, организации и тренеры в одной выдаче
 * (ТЗ п. 4.6.2–4.6.4, 4.7, 4.9.4, 4.10.3).
 * <p>
 * Каталог объединяет разные сущности, поэтому выдача строится одним SQL-запросом
 * (UNION ALL) с общей проекцией: так сохраняются единая сортировка, точное число
 * результатов и постраничный вывод по всей выдаче, а не по каждому типу отдельно.
 * Текстовый поиск внутри ветвей использует GIN-индексы trigram (п. 4.7.4).
 */
@Repository
public class CatalogRepository {

    /** Часовой пояс Платформы: границы суток в фильтре по дате (п. 4.2.17). */
    public static final ZoneId ZONE = ZoneId.of("Europe/Minsk");

    /** Символ экранирования в шаблоне LIKE (один символ — требование PostgreSQL). */
    private static final String ESCAPE = "\\";

    /** Имена параметров запроса в тексте SQL. */
    private static final Pattern PARAMETER = Pattern.compile("(?<!:):(\\w+)");

    private final EntityManager entityManager;

    public CatalogRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /** Страница каталога вместе с общим числом найденного (п. 4.7.7–4.7.8). */
    public CatalogPage findPage(CatalogCriteria criteria, CatalogSort sort, int page, int size, OffsetDateTime now) {
        Sql sql = union(criteria, now, Dimension.NONE);
        Query query = nativeQuery(sql.text() + order(sort) + " LIMIT :size OFFSET :offset", sql.params());
        query.setParameter("size", size);
        query.setParameter("offset", (long) page * size);
        List<CatalogRow> rows = new ArrayList<>();
        for (Object row : query.getResultList()) {
            rows.add(readRow((Object[]) row));
        }
        Query counter = nativeQuery("SELECT count(*) FROM (" + sql.text() + ") c", sql.params());
        Object total = counter.getSingleResult();
        return new CatalogPage(rows, ((Number) total).longValue());
    }

    /**
     * Число объектов каждого типа при текущих фильтрах — счетчики панели фильтров.
     * Фильтр по типам при подсчете игнорируется: счетчик показывает, сколько объектов
     * будет найдено при выборе каждого типа (п. 4.7.7).
     */
    public Map<CatalogObjectType, Long> countByType(CatalogCriteria criteria, OffsetDateTime now) {
        Sql sql = union(criteria, now, Dimension.TYPE);
        Query query = nativeQuery(
                "SELECT c.object_type, count(*) FROM (" + sql.text() + ") c GROUP BY c.object_type", sql.params());
        Map<CatalogObjectType, Long> counts = new LinkedHashMap<>();
        for (CatalogObjectType type : CatalogObjectType.values()) {
            counts.put(type, 0L);
        }
        for (Object row : query.getResultList()) {
            Object[] values = (Object[]) row;
            counts.put(CatalogObjectType.valueOf((String) values[0]), ((Number) values[1]).longValue());
        }
        return counts;
    }

    /**
     * Число объектов по каждому виду спорта — счетчики панели фильтров.
     * Фильтр по виду спорта при подсчете игнорируется: счетчик показывает, сколько объектов
     * будет найдено при выборе каждого вида спорта (п. 4.7.7).
     *
     * @return число объектов по идентификатору вида спорта
     */
    public Map<Long, Long> countBySport(CatalogCriteria criteria, OffsetDateTime now) {
        Sql sql = union(criteria, now, Dimension.SPORT);
        Query query = nativeQuery("SELECT s.sport_id, count(DISTINCT c.id) FROM (" + sql.text() + ") c "
                + "JOIN LATERAL unnest(c.sport_ids) AS s(sport_id) ON true GROUP BY s.sport_id", sql.params());
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Object row : query.getResultList()) {
            Object[] values = (Object[]) row;
            counts.put(((Number) values[0]).longValue(), ((Number) values[1]).longValue());
        }
        return counts;
    }

    /** Метки карты: опубликованные мероприятия с координатами населенного пункта. */
    public List<MapMarker> findMarkers(CatalogCriteria criteria, OffsetDateTime now) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("now", now);
        StringBuilder sql = new StringBuilder("""
                SELECT e.id, e.title_ru, e.title_en, e.starts_at, e.locality,
                       r.name_ru AS region_ru, r.name_en AS region_en,
                       coalesce(l.latitude, r.latitude) AS latitude,
                       coalesce(l.longitude, r.longitude) AS longitude
                FROM event e
                JOIN region r ON r.id = e.region_id
                LEFT JOIN locality l ON lower(l.name_ru) = lower(e.locality)
                WHERE e.status = 'PUBLISHED' AND coalesce(e.ends_at, e.starts_at) >= :now
                """);
        if (criteria.regionId() != null) {
            sql.append(" AND e.region_id = :regionId");
            params.put("regionId", criteria.regionId());
        }
        if (criteria.city() != null && !criteria.city().isBlank()) {
            sql.append(" AND lower(e.locality) = lower(:city)");
            params.put("city", criteria.city().trim());
        }
        if (criteria.sportId() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM event_sport es JOIN sport s ON s.id = es.sport_id "
                    + "WHERE es.event_id = e.id AND s.id = :sportId)");
            params.put("sportId", criteria.sportId());
        }
        if (criteria.dateFrom() != null) {
            sql.append(" AND e.starts_at >= :dateFrom");
            params.put("dateFrom", criteria.dateFrom().atStartOfDay(ZONE).toOffsetDateTime());
        }
        if (criteria.dateTo() != null) {
            sql.append(" AND e.starts_at < :dateTo");
            params.put("dateTo", criteria.dateTo().plusDays(1).atStartOfDay(ZONE).toOffsetDateTime());
        }
        sql.append(" ORDER BY e.starts_at");
        Query query = nativeQuery(sql.toString(), params);
        List<MapMarker> markers = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] values = (Object[]) row;
            markers.add(new MapMarker(
                    ((Number) values[0]).longValue(),
                    (String) values[1],
                    (String) values[2],
                    toOffset(values[3]),
                    (String) values[4],
                    (String) values[5],
                    (String) values[6],
                    toDecimal(values[7]),
                    toDecimal(values[8])));
        }
        return markers;
    }

    /**
     * Текст объединенной выдачи и его параметры.
     *
     * @param ignored измерение, счетчик которого считается без учета собственного фильтра
     */
    private Sql union(CatalogCriteria criteria, OffsetDateTime now, Dimension ignored) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("now", now);
        // Фильтры, применимые только к мероприятиям (возраст, цена, дата, формат, места),
        // исключают поставщиков: у их карточек таких сведений нет (п. 4.10.4, 5.2).
        // Счетчики панели фильтров показывают то же, что вернет выдача: поставщики при
        // таком фильтре не находятся (п. 4.7.7).
        boolean eventOnly = criteria.eventsOnly() || criteria.providerId() != null;
        boolean ignoreType = ignored == Dimension.TYPE;
        boolean wantsEvents = ignoreType || criteria.types().isEmpty()
                || criteria.types().contains(CatalogObjectType.EVENT);
        boolean wantsProviders = !eventOnly && (ignoreType || criteria.types().isEmpty()
                || criteria.types().stream().anyMatch(CatalogObjectType::isProvider));
        List<String> branches = new ArrayList<>();
        if (wantsEvents) {
            branches.add(eventBranch(criteria, params, ignored));
        }
        if (wantsProviders) {
            branches.add(providerBranch(criteria, params, ignored));
        }
        if (branches.isEmpty()) {
            branches.add(emptyBranch());
        }
        return new Sql("SELECT c.* FROM (" + String.join(" UNION ALL ", branches) + ") c", params);
    }

    private String eventBranch(CatalogCriteria criteria, Map<String, Object> params, Dimension ignored) {
        StringBuilder sql = new StringBuilder("""
                SELECT 'EVENT'::text AS object_type,
                       e.id AS id,
                       e.title_ru::text AS title_ru, e.title_en::text AS title_en,
                       e.description_ru::text AS description_ru, e.description_en::text AS description_en,
                       e.published_at AS published_at,
                       e.starts_at AS starts_at, e.ends_at AS ends_at,
                       r.name_ru::text AS region_ru, r.name_en::text AS region_en,
                       e.district::text AS district, e.locality::text AS locality,
                       e.street::text AS street, e.building::text AS building,
                       e.type::text AS format,
                       e.age_min AS age_min, e.age_max AS age_max,
                       e.participant_limit AS participant_limit,
                       (SELECT min(o.price) FROM event_offer o WHERE o.event_id = e.id) AS price,
                       (SELECT count(*) FROM booking b WHERE b.event_id = e.id
                            AND b.status IN ('SUBMITTED', 'CONFIRMED')) AS taken_seats,
                       ARRAY(SELECT s.code::text FROM event_sport es JOIN sport s ON s.id = es.sport_id
                            WHERE es.event_id = e.id) AS sport_codes,
                       ARRAY(SELECT s.id FROM event_sport es JOIN sport s ON s.id = es.sport_id
                            WHERE es.event_id = e.id) AS sport_ids,
                       ARRAY(SELECT s.name_ru::text FROM event_sport es JOIN sport s ON s.id = es.sport_id
                            WHERE es.event_id = e.id ORDER BY s.name_ru) AS sport_names_ru,
                       ARRAY(SELECT s.name_en::text FROM event_sport es JOIN sport s ON s.id = es.sport_id
                            WHERE es.event_id = e.id ORDER BY s.name_ru) AS sport_names_en,
                       (SELECT m.id FROM event_media m WHERE m.event_id = e.id
                            AND m.published AND m.type = 'IMAGE' ORDER BY m.id LIMIT 1) AS image_media_id,
                       pr.name_ru::text AS organizer_ru, pr.name_en::text AS organizer_en
                FROM event e
                JOIN region r ON r.id = e.region_id
                JOIN provider pr ON pr.id = e.provider_id
                WHERE e.status = 'PUBLISHED' AND coalesce(e.ends_at, e.starts_at) >= :now
                """);
        appendEventFilters(sql, params, criteria, ignored);
        return sql.toString();
    }

    private void appendEventFilters(StringBuilder sql, Map<String, Object> params, CatalogCriteria criteria,
                                     Dimension ignored) {
        if (criteria.providerId() != null) {
            sql.append(" AND e.provider_id = :providerId");
            params.put("providerId", criteria.providerId());
        }
        if (criteria.regionId() != null) {
            sql.append(" AND e.region_id = :regionId");
            params.put("regionId", criteria.regionId());
        }
        if (criteria.city() != null && !criteria.city().isBlank()) {
            sql.append(" AND lower(e.locality) = lower(:city)");
            params.put("city", criteria.city().trim());
        }
        if (criteria.sportId() != null && ignored != Dimension.SPORT) {
            sql.append(" AND EXISTS (SELECT 1 FROM event_sport es JOIN sport s ON s.id = es.sport_id "
                    + "WHERE es.event_id = e.id AND s.id = :sportId)");
            params.put("sportId", criteria.sportId());
        }
        if (criteria.eventType() != null) {
            sql.append(" AND e.type = :format");
            params.put("format", criteria.eventType().name());
        }
        if (criteria.age() != null) {
            sql.append(" AND (e.age_min IS NULL OR e.age_min <= :age) "
                    + "AND (e.age_max IS NULL OR e.age_max >= :age)");
            params.put("age", criteria.age());
        }
        if (criteria.priceMin() != null || criteria.priceMax() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM event_offer o WHERE o.event_id = e.id");
            if (criteria.priceMin() != null) {
                sql.append(" AND o.price >= :priceMin");
                params.put("priceMin", criteria.priceMin());
            }
            if (criteria.priceMax() != null) {
                sql.append(" AND o.price <= :priceMax");
                params.put("priceMax", criteria.priceMax());
            }
            sql.append(")");
        }
        if (criteria.dateFrom() != null) {
            sql.append(" AND e.starts_at >= :dateFrom");
            params.put("dateFrom", criteria.dateFrom().atStartOfDay(ZONE).toOffsetDateTime());
        }
        if (criteria.dateTo() != null) {
            sql.append(" AND e.starts_at < :dateTo");
            params.put("dateTo", criteria.dateTo().plusDays(1).atStartOfDay(ZONE).toOffsetDateTime());
        }
        if (criteria.onlyWithFreeSeats()) {
            sql.append(" AND (e.participant_limit IS NULL OR e.participant_limit > "
                    + "(SELECT count(*) FROM booking b WHERE b.event_id = e.id "
                    + "AND b.status IN ('SUBMITTED', 'CONFIRMED')))");
        }
        appendTextFilter(sql, params, criteria, "e", "title",
                "event_sport es JOIN sport s ON s.id = es.sport_id", "es.event_id = e.id");
    }

    private String providerBranch(CatalogCriteria criteria, Map<String, Object> params, Dimension ignored) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.type::text AS object_type,
                       p.id AS id,
                       p.name_ru::text AS title_ru, p.name_en::text AS title_en,
                       p.description_ru::text AS description_ru, p.description_en::text AS description_en,
                       p.published_at AS published_at,
                       NULL::timestamptz AS starts_at, NULL::timestamptz AS ends_at,
                       r.name_ru::text AS region_ru, r.name_en::text AS region_en,
                       p.district::text AS district, p.locality::text AS locality,
                       p.street::text AS street, p.building::text AS building,
                       NULL::text AS format,
                       NULL::integer AS age_min, NULL::integer AS age_max,
                       NULL::integer AS participant_limit,
                       NULL::numeric AS price, 0::bigint AS taken_seats,
                       ARRAY(SELECT s.code::text FROM provider_sport ps JOIN sport s ON s.id = ps.sport_id
                            WHERE ps.provider_id = p.id) AS sport_codes,
                       ARRAY(SELECT s.id FROM provider_sport ps JOIN sport s ON s.id = ps.sport_id
                            WHERE ps.provider_id = p.id) AS sport_ids,
                       ARRAY(SELECT s.name_ru::text FROM provider_sport ps JOIN sport s ON s.id = ps.sport_id
                            WHERE ps.provider_id = p.id ORDER BY s.name_ru) AS sport_names_ru,
                       ARRAY(SELECT s.name_en::text FROM provider_sport ps JOIN sport s ON s.id = ps.sport_id
                            WHERE ps.provider_id = p.id ORDER BY s.name_ru) AS sport_names_en,
                       NULL::bigint AS image_media_id,
                       NULL::text AS organizer_ru, NULL::text AS organizer_en
                FROM provider p
                JOIN region r ON r.id = p.region_id
                WHERE p.status = 'PUBLISHED'
                """);
        if (ignored != Dimension.TYPE && !criteria.types().isEmpty()) {
            List<String> providerTypes = criteria.types().stream()
                    .filter(CatalogObjectType::isProvider)
                    .map(Enum::name)
                    .toList();
            if (!providerTypes.isEmpty()) {
                sql.append(" AND p.type IN (:providerTypes)");
                params.put("providerTypes", providerTypes);
            }
        }
        if (criteria.regionId() != null) {
            sql.append(" AND p.region_id = :regionId");
            params.put("regionId", criteria.regionId());
        }
        if (criteria.city() != null && !criteria.city().isBlank()) {
            sql.append(" AND lower(p.locality) = lower(:city)");
            params.put("city", criteria.city().trim());
        }
        if (criteria.sportId() != null && ignored != Dimension.SPORT) {
            sql.append(" AND EXISTS (SELECT 1 FROM provider_sport ps JOIN sport s ON s.id = ps.sport_id "
                    + "WHERE ps.provider_id = p.id AND s.id = :sportId)");
            params.put("sportId", criteria.sportId());
        }
        appendTextFilter(sql, params, criteria, "p", "name",
                "provider_sport ps JOIN sport s ON s.id = ps.sport_id", "ps.provider_id = p.id");
        return sql.toString();
    }

    /**
     * Текстовый поиск по наименованию, описанию, адресу и видам спорта (п. 4.7.1),
     * регистронезависимо и по частичному совпадению (п. 4.7.4).
     *
     * @param alias      таблица объекта
     * @param title      столбцы наименования (у мероприятия — title_*, у поставщика — name_*)
     * @param sportJoin  соединение с видами спорта
     * @param sportLink  условие связи объекта и вида спорта
     */
    private void appendTextFilter(StringBuilder sql, Map<String, Object> params, CatalogCriteria criteria,
                                  String alias, String title, String sportJoin, String sportLink) {
        if (criteria.query() == null || criteria.query().isBlank()) {
            return;
        }
        sql.append(" AND (lower(").append(alias).append('.').append(title).append("_ru) LIKE :pattern ESCAPE '")
                .append(ESCAPE).append("'");
        sql.append(" OR lower(").append(alias).append('.').append(title).append("_en) LIKE :pattern ESCAPE '")
                .append(ESCAPE).append("'");
        sql.append(" OR lower(").append(alias).append(".description_ru) LIKE :pattern ESCAPE '").append(ESCAPE).append("'");
        sql.append(" OR lower(").append(alias).append(".description_en) LIKE :pattern ESCAPE '").append(ESCAPE).append("'");
        sql.append(" OR lower(").append(alias).append(".locality) LIKE :pattern ESCAPE '").append(ESCAPE).append("'");
        sql.append(" OR lower(").append(alias).append(".street) LIKE :pattern ESCAPE '").append(ESCAPE).append("'");
        sql.append(" OR lower(").append(alias).append(".district) LIKE :pattern ESCAPE '").append(ESCAPE).append("'");
        sql.append(" OR EXISTS (SELECT 1 FROM ").append(sportJoin)
                .append(" WHERE ").append(sportLink)
                .append(" AND (lower(s.name_ru) LIKE :pattern ESCAPE '").append(ESCAPE).append("'")
                .append(" OR lower(s.name_en) LIKE :pattern ESCAPE '").append(ESCAPE).append("')))");
        params.put("pattern", likePattern(criteria.query()));
    }

    /** Порядок выдачи: по новизне (п. 4.7.3), по дате проведения или по цене. */
    private String order(CatalogSort sort) {
        return switch (sort) {
            case NEWEST -> " ORDER BY c.published_at DESC NULLS LAST, c.id DESC";
            case DATE -> " ORDER BY c.starts_at ASC NULLS LAST, c.published_at DESC NULLS LAST, c.id DESC";
            case PRICE -> " ORDER BY c.price ASC NULLS LAST, c.published_at DESC NULLS LAST, c.id DESC";
        };
    }

    /** Пустая ветвь: выдача без объектов (например, выбран только тип, которого нет в фильтре). */
    private String emptyBranch() {
        return """
                SELECT 'EVENT'::text AS object_type, NULL::bigint AS id, NULL::text AS title_ru, NULL::text AS title_en,
                       NULL::text AS description_ru, NULL::text AS description_en, NULL::timestamptz AS published_at,
                       NULL::timestamptz AS starts_at, NULL::timestamptz AS ends_at,
                       NULL::text AS region_ru, NULL::text AS region_en, NULL::text AS district, NULL::text AS locality,
                       NULL::text AS street, NULL::text AS building, NULL::text AS format,
                       NULL::integer AS age_min, NULL::integer AS age_max, NULL::integer AS participant_limit,
                       NULL::numeric AS price, 0::bigint AS taken_seats, ARRAY[]::text[] AS sport_codes,
                       ARRAY[]::bigint[] AS sport_ids,
                       ARRAY[]::text[] AS sport_names_ru, ARRAY[]::text[] AS sport_names_en,
                       NULL::bigint AS image_media_id, NULL::text AS organizer_ru, NULL::text AS organizer_en
                WHERE false
                """;
    }

    /** Шаблон LIKE с экранированием спецсимволов (п. 4.7.4). */
    private static String likePattern(String query) {
        String escaped = query.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /**
     * Нативный запрос с именованными параметрами. Hibernate выполняет отложенный сброс
     * изменений перед нативным запросом, поэтому данные, созданные в этой же транзакции, видны сразу.
     */
    private Query nativeQuery(String sql, Map<String, Object> params) {
        Query query = entityManager.createNativeQuery(sql);
        Set<String> used = usedParameters(sql);
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (used.contains(entry.getKey())) {
                query.setParameter(entry.getKey(), entry.getValue());
            }
        }
        return query;
    }

    /** Имена параметров, реально встречающихся в тексте запроса. */
    private static Set<String> usedParameters(String sql) {
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = PARAMETER.matcher(sql);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static CatalogRow readRow(Object[] row) {
        try {
            return new CatalogRow(
                    (String) row[0],
                    ((Number) row[1]).longValue(),
                    (String) row[2],
                    (String) row[3],
                    (String) row[4],
                    (String) row[5],
                    toOffset(row[6]),
                    toOffset(row[7]),
                    toOffset(row[8]),
                    (String) row[9],
                    (String) row[10],
                    (String) row[11],
                    (String) row[12],
                    (String) row[13],
                    (String) row[14],
                    (String) row[15],
                    (Integer) row[16],
                    (Integer) row[17],
                    (Integer) row[18],
                    (BigDecimal) row[19],
                    ((Number) row[20]).longValue(),
                    textArray(row[21]),
                    textArray(row[23]),
                    textArray(row[24]),
                    row[25] == null ? null : ((Number) row[25]).longValue(),
                    (String) row[26],
                    (String) row[27]);
        } catch (SQLException e) {
            throw new IllegalStateException("Не удалось прочитать строку каталога", e);
        }
    }

    private static List<String> textArray(Object value) throws SQLException {
        if (value instanceof String[] array) {
            return List.of(array);
        }
        if (value instanceof Array array) {
            Object[] values = (Object[]) array.getArray();
            List<String> result = new ArrayList<>(values.length);
            for (Object element : values) {
                result.add(element == null ? null : element.toString());
            }
            return result;
        }
        return List.of();
    }

    private static OffsetDateTime toOffset(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof Instant instant) {
            return instant.atOffset(ZONE.getRules().getOffset(instant));
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZONE.getRules().getOffset(timestamp.toInstant()));
        }
        return null;
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    /** Измерение каталога, счетчик которого считается независимо от собственного фильтра. */
    private enum Dimension {
        /** Ничего не игнорируется: обычная выдача. */
        NONE,
        /** Счетчик по типам объектов не учитывает фильтр по типам. */
        TYPE,
        /** Счетчик по видам спорта не учитывает фильтр по виду спорта. */
        SPORT
    }

    /** Страница выдачи каталога. */
    public record CatalogPage(List<CatalogRow> rows, long totalElements) {
    }

    /** Строка объединенной выдачи. */
    public record CatalogRow(String objectType,
                             long id,
                             String titleRu,
                             String titleEn,
                             String descriptionRu,
                             String descriptionEn,
                             OffsetDateTime publishedAt,
                             OffsetDateTime startsAt,
                             OffsetDateTime endsAt,
                             String regionRu,
                             String regionEn,
                             String district,
                             String locality,
                             String street,
                             String building,
                             String format,
                             Integer ageMin,
                             Integer ageMax,
                             Integer participantLimit,
                             BigDecimal price,
                             long takenSeats,
                             List<String> sportCodes,
                             List<String> sportNamesRu,
                             List<String> sportNamesEn,
                             Long imageMediaId,
                             String organizerRu,
                             String organizerEn) {

        /** Тип объекта выдачи. */
        public CatalogObjectType type() {
            return CatalogObjectType.valueOf(objectType);
        }
    }

    /** Метка мероприятия на карте. */
    public record MapMarker(Long id,
                            String titleRu,
                            String titleEn,
                            OffsetDateTime startsAt,
                            String locality,
                            String regionRu,
                            String regionEn,
                            BigDecimal latitude,
                            BigDecimal longitude) {
    }

    private record Sql(String text, Map<String, Object> params) {
    }
}
