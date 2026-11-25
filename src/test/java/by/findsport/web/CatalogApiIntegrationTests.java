package by.findsport.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import by.findsport.TestcontainersConfiguration;
import by.findsport.domain.Address;
import by.findsport.domain.CardStatus;
import by.findsport.domain.EventType;
import by.findsport.domain.LocalizedText;
import by.findsport.domain.Provider;
import by.findsport.domain.ProviderType;
import by.findsport.domain.Region;
import by.findsport.domain.Sport;
import by.findsport.domain.User;
import by.findsport.domain.UserRole;
import by.findsport.repository.ProviderRepository;
import by.findsport.repository.RegionRepository;
import by.findsport.repository.SportRepository;
import by.findsport.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Каталог, поиск, фильтры и карта (ТЗ п. 4.6.2–4.6.4, 4.7, 4.9.4, 4.10.3):
 * поиск по всем типам объектов и полям, регистронезависимость и частичное совпадение,
 * совместная работа фильтров, сортировка, общее число результатов и постраничный вывод,
 * сохранение параметров в URL, метки карты и отсутствие черновиков в публичной выдаче.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class CatalogApiIntegrationTests {

    private static final String PASSWORD = "Secret-123";
    private static final String OWNER = "catalog-owner@example.by";
    private static final String MODERATOR = "catalog-moderator@example.by";
    /** Мероприятия начинаются через 10 дней — так дата в фильтре однозначна. */
    private static final int START_IN_DAYS = 10;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserRepository users;
    @Autowired
    private RegionRepository regions;
    @Autowired
    private SportRepository sports;
    @Autowired
    private ProviderRepository providers;

    private Region minsk;
    private Region gomel;
    private Sport running;
    private Sport swimming;
    private Provider provider;

    @BeforeEach
    void setUp() {
        minsk = regions.findAll().stream().filter(r -> r.getCode().equals("MINSK")).findFirst().orElseThrow();
        gomel = regions.findAll().stream().filter(r -> r.getCode().equals("GOMEL")).findFirst().orElseThrow();
        running = sports.save(new Sport("RUNNING", new LocalizedText("Бег", "Running")));
        swimming = sports.save(new Sport("SWIMMING", new LocalizedText("Плавание", "Swimming")));

        User owner = new User(OWNER, passwordEncoder.encode(PASSWORD), UserRole.PROVIDER);
        owner.setEmailVerified(true);
        users.save(owner);
        User moderator = new User(MODERATOR, passwordEncoder.encode(PASSWORD), UserRole.MODERATOR);
        moderator.setEmailVerified(true);
        users.save(moderator);

        provider = new Provider(owner, ProviderType.ORGANIZATION);
        provider.setName(new LocalizedText("Беговой клуб «Старт»", "Running club Start"));
        provider.setDescription(new LocalizedText("Групповые тренировки по бегу", null));
        provider.setRegion(minsk);
        provider.setAddress(new Address(null, "Минск", "ул. Спортивная", "1"));
        provider.setWorkingHours(new LocalizedText("Пн–Пт 9:00–18:00", null));
        provider.setContactEmail("club@example.by");
        provider.getSports().add(running);
        provider.setStatus(CardStatus.PUBLISHED);
        providers.save(provider);
    }

    /** Поиск по всем типам объектов и полям (ТЗ п. 4.7.1, 4.10.3): наименование, описание, спорт, город. */
    @Test
    void searchCoversEventsAndProvidersByAllFields() throws Exception {
        publishEvent("Минский полумарафон", "Открытый забег по городу", running, minsk, "Минск", 30, 10);
        publishEvent("Гомельский турнир", "Открытый турнир по плаванию", swimming, gomel, "Гомель", 40, 0);

        mvc.perform(get("/api/v1/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.countsByType.EVENT").value(2))
                .andExpect(jsonPath("$.countsByType.ORGANIZATION").value(1))
                .andExpect(jsonPath("$.countsByType.TRAINER").value(0));

        // По названию мероприятия
        mvc.perform(get("/api/v1/catalog").param("q", "полумарафон"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Минский полумарафон"));
        // По описанию мероприятия
        mvc.perform(get("/api/v1/catalog").param("q", "по плаванию"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Гомельский турнир"));
        // По наименованию поставщика
        mvc.perform(get("/api/v1/catalog").param("q", "Старт"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].objectType").value("ORGANIZATION"));
        // По названию вида спорта
        mvc.perform(get("/api/v1/catalog").param("q", "плавание"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Гомельский турнир"));
        // По населенному пункту
        mvc.perform(get("/api/v1/catalog").param("q", "Гомель"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    /** Регистронезависимость и частичное совпадение (ТЗ п. 4.7.4). */
    @Test
    void searchIsCaseInsensitiveAndMatchesPartially() throws Exception {
        publishEvent("Минский полумарафон", "Открытый забег", running, minsk, "Минск", 30, 10);

        mvc.perform(get("/api/v1/catalog").param("q", "ПОЛУМАРАФОН"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Минский полумарафон"));
        mvc.perform(get("/api/v1/catalog").param("q", "полумарафон"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/catalog").param("q", "мараф"))
                .andExpect(jsonPath("$.totalElements").value(1));
        // Спецсимволы LIKE не ломают поиск
        mvc.perform(get("/api/v1/catalog").param("q", "100%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /** Совместная работа фильтров (ТЗ п. 4.7.2): область, город, вид спорта, возраст, цена, дата, места. */
    @Test
    void filtersApplyTogether() throws Exception {
        publishEvent("Минский полумарафон", "Забег", running, minsk, "Минск", 30, 10);
        publishEvent("Гомельский турнир", "Плавание", swimming, gomel, "Гомель", 40, 0);

        mvc.perform(get("/api/v1/catalog")
                        .param("regionId", String.valueOf(minsk.getId()))
                        .param("city", "минск")
                        .param("sportId", String.valueOf(running.getId()))
                        .param("age", "35")
                        .param("priceMax", "40")
                        .param("dateFrom", startDate().toString())
                        .param("dateTo", startDate().toString())
                        .param("hasFreeSeats", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Минский полумарафон"));

        // Несовпадение одного из фильтров исключает результат
        mvc.perform(get("/api/v1/catalog").param("sportId", String.valueOf(swimming.getId()))
                        .param("city", "Минск"))
                .andExpect(jsonPath("$.totalElements").value(0));
        // Цена выше всех предложений не подходит
        mvc.perform(get("/api/v1/catalog").param("priceMin", "500"))
                .andExpect(jsonPath("$.totalElements").value(0));
        // Возраст вне диапазона карточки
        mvc.perform(get("/api/v1/catalog").param("age", "70"))
                .andExpect(jsonPath("$.totalElements").value(0));
        // Другая область
        mvc.perform(get("/api/v1/catalog").param("regionId", String.valueOf(gomel.getId())))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Гомельский турнир"));
    }

    /** Фильтр по формату мероприятия (ТЗ п. 4.9.4) — применим только к мероприятиям. */
    @Test
    void filterByEventFormat() throws Exception {
        publishEvent("Турнир", "Забег", running, minsk, "Минск", 30, 10, EventType.TOURNAMENT);
        publishEvent("Тренировка", "Забег", running, minsk, "Минск", 30, 10, EventType.TRAINING);

        mvc.perform(get("/api/v1/catalog").param("format", "TOURNAMENT"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Турнир"));
        mvc.perform(get("/api/v1/catalog").param("format", "TRAINING"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Тренировка"));
    }

    /** Сортировка, общее число результатов и постраничный вывод (ТЗ п. 4.7.3, 4.7.7–4.7.8). */
    @Test
    void resultsAreSortedAndPaged() throws Exception {
        publishEvent("Первый", "Один", running, minsk, "Минск", 30, 10, EventType.TOURNAMENT, CardStatus.PUBLISHED, 1);
        publishEvent("Второй", "Два", running, minsk, "Минск", 30, 10, EventType.TOURNAMENT, CardStatus.PUBLISHED, 2);
        publishEvent("Третий", "Три", running, minsk, "Минск", 30, 10, EventType.TOURNAMENT, CardStatus.PUBLISHED, 3);

        mvc.perform(get("/api/v1/catalog").param("type", "EVENT").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));

        // Вторая страница содержит оставшиеся объекты
        mvc.perform(get("/api/v1/catalog").param("type", "EVENT").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.content.length()").value(1));

        // Порядок по новизне: последний опубликованный — первый
        mvc.perform(get("/api/v1/catalog").param("type", "EVENT"))
                .andExpect(jsonPath("$.content[0].title").value("Третий"));
        // Порядок по дате проведения — по возрастанию
        mvc.perform(get("/api/v1/catalog").param("type", "EVENT").param("sort", "DATE"))
                .andExpect(jsonPath("$.content[0].title").value("Первый"));
        // Неизвестный порядок сортировки — понятная ошибка
        mvc.perform(get("/api/v1/catalog").param("sort", "RANDOM"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
    }

    /** Параметры поиска отражаются в URL (ТЗ п. 4.7.9) и применяются сервером. */
    @Test
    void searchParametersAreReflectedInUrl() throws Exception {
        publishEvent("Минский полумарафон", "Забег", running, minsk, "Минск", 30, 10);

        mvc.perform(get("/api/v1/catalog")
                        .param("q", "полумарафон")
                        .param("regionId", String.valueOf(minsk.getId()))
                        .param("sportId", String.valueOf(running.getId()))
                        .param("format", "TOURNAMENT")
                        .param("age", "35")
                        .param("priceMin", "10")
                        .param("priceMax", "50")
                        .param("dateFrom", startDate().toString())
                        .param("dateTo", startDate().toString())
                        .param("hasFreeSeats", "true")
                        .param("sort", "NEWEST")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    /** Метки карты и переход по метке к карточке (ТЗ п. 4.6.2, 4.9.3). */
    @Test
    void mapReturnsMarkersWithCoordinates() throws Exception {
        long eventId = publishEvent("Минский полумарафон", "Забег", running, minsk, "Минск", 30, 10);

        mvc.perform(get("/api/v1/catalog/map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value((int) eventId))
                .andExpect(jsonPath("$[0].title").value("Минский полумарафон"))
                .andExpect(jsonPath("$[0].locality").value("Минск"))
                .andExpect(jsonPath("$[0].latitude").value(53.9006))
                .andExpect(jsonPath("$[0].longitude").value(27.559));

        // Карточка мероприятия открывается по идентификатору метки
        mvc.perform(get("/api/v1/events/{id}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Минский полумарафон"));
    }

    /** Черновики, отклоненные и архивные записи в публичной выдаче не показываются (ТЗ п. 4.2.8, 4.14.1). */
    @Test
    void onlyPublishedObjectsAreShownToVisitors() throws Exception {
        publishEvent("Опубликованный забег", "Забег", running, minsk, "Минск", 30, 10);
        publishEvent("Черновик забега", "Забег", running, minsk, "Минск", 30, 10, EventType.TRAINING, CardStatus.DRAFT);

        mvc.perform(get("/api/v1/catalog"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].title", not(hasItem("Черновик забега"))));
        mvc.perform(get("/api/v1/catalog/map"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    /** Выбор типа объекта ограничивает выдачу (ТЗ п. 4.6.2). */
    @Test
    void objectTypeFilterNarrowsResults() throws Exception {
        publishEvent("Минский полумарафон", "Забег", running, minsk, "Минск", 30, 10);

        mvc.perform(get("/api/v1/catalog").param("type", "EVENT"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].objectType").value("EVENT"));
        mvc.perform(get("/api/v1/catalog").param("type", "TRAINER"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/catalog").param("type", "ORGANIZATION"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].objectType").value("ORGANIZATION"));
    }

    /** Главная страница: ближайшие мероприятия и быстрый выбор вида спорта и региона (ТЗ п. 4.6.3). */
    @Test
    void homePageShowsNearestEventsAndDictionaries() throws Exception {
        publishEvent("Ближайший забег", "Забег", running, minsk, "Минск", 30, 10);

        mvc.perform(get("/api/v1/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nearestEvents.length()").value(1))
                .andExpect(jsonPath("$.nearestEvents[0].title").value("Ближайший забег"))
                .andExpect(jsonPath("$.popularSports[?(@.code == 'RUNNING')].eventCount").value(1))
                .andExpect(jsonPath("$.regions[?(@.code == 'MINSK')].name").value("г. Минск"));
    }

    private long publishEvent(String title, String description, Sport sport, Region region, String locality,
                              int ageMin, int price) throws Exception {
        return publishEvent(title, description, sport, region, locality, ageMin, price, EventType.TOURNAMENT,
                CardStatus.PUBLISHED, 0);
    }

    private long publishEvent(String title, String description, Sport sport, Region region, String locality,
                              int ageMin, int price, EventType type) throws Exception {
        return publishEvent(title, description, sport, region, locality, ageMin, price, type, CardStatus.PUBLISHED, 0);
    }

    private long publishEvent(String title, String description, Sport sport, Region region, String locality,
                              int ageMin, int price, EventType type, CardStatus status) throws Exception {
        return publishEvent(title, description, sport, region, locality, ageMin, price, type, status, 0);
    }

    /**
     * @param startInDays сдвиг начала мероприятия относительно текущего дня — разные значения
     *                     дают однозначный порядок по дате проведения
     */
    private long publishEvent(String title, String description, Sport sport, Region region, String locality,
                              int ageMin, int price, EventType type, CardStatus status, int startInDays)
            throws Exception {
        OffsetDateTime start = OffsetDateTime.now(ZoneId.of("Europe/Minsk"))
                .plusDays(START_IN_DAYS + startInDays).withHour(10).withMinute(0).withSecond(0).withNano(0);
        ObjectNode body = json.createObjectNode()
                .put("providerId", provider.getId())
                .put("type", type.name())
                .put("startsAt", start.toString())
                .put("endsAt", start.plusHours(3).toString())
                .put("regionId", region.getId())
                .put("ageMin", ageMin)
                .put("ageMax", ageMin + 10)
                .put("participantLimit", 50)
                .put("contactPhone", "+375291234567")
                .put("contactEmail", "org@example.by");
        body.putObject("title").put("ru", title);
        body.putObject("description").put("ru", description);
        body.putObject("conditions").put("ru", "Условия участия");
        body.putArray("sportIds").add(sport.getId());
        body.putObject("address").put("locality", locality).put("street", "ул. Спортивная").put("building", "1");
        body.putArray("offers").addObject().put("price", String.valueOf(price))
                .putObject("title").put("ru", "Участие");

        String response = mvc.perform(post("/api/v1/provider/events").with(as(OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(response).get("id").asLong();
        if (status == CardStatus.PUBLISHED) {
            mvc.perform(post("/api/v1/provider/events/{id}/submit", id).with(as(OWNER)))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/v1/moderation/events/{id}/approve", id).with(as(MODERATOR)))
                    .andExpect(status().isOk());
        }
        return id;
    }

    /** Дата начала мероприятия — для фильтра по датам. */
    private static LocalDate startDate() {
        return LocalDate.now(ZoneId.of("Europe/Minsk")).plusDays(START_IN_DAYS);
    }

    /** Аутентифицированная сессия с ролью из БД и CSRF-токеном. */
    private RequestPostProcessor as(String email) {
        String role = users.findByEmail(email).orElseThrow().getRole().name();
        return request -> csrf().postProcessRequest(user(email).roles(role).postProcessRequest(request));
    }
}
