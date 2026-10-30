package by.findsport.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import by.findsport.TestcontainersConfiguration;
import by.findsport.domain.Address;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Каркас REST API на реальном PostgreSQL: приемочный сценарий «Создание карточки» (ТЗ Приложение 3),
 * матрица ролей (Приложение 2) и валидация входящих данных (п. 4.2.13, 6.7).
 * Каждый тест откатывает свои данные транзакцией.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class EventApiIntegrationTests {

    private static final String PASSWORD = "Secret-123";

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
    private Sport football;
    private Provider provider;
    /** Одна дата на тест: сдвиг даты — существенное изменение карточки. */
    private OffsetDateTime start;

    @BeforeEach
    void setUp() {
        start = OffsetDateTime.now(ZoneId.of("Europe/Minsk")).plusDays(10).withNano(0);
        minsk = regions.findAll().stream().filter(r -> r.getCode().equals("MINSK")).findFirst().orElseThrow();
        football = sports.save(new Sport("FOOTBALL", new LocalizedText("Футбол", "Football")));
        User owner = createUser("provider@example.by", UserRole.PROVIDER);
        createUser("moderator@example.by", UserRole.MODERATOR);
        createUser("user@example.by", UserRole.USER);

        provider = new Provider(owner, ProviderType.ORGANIZATION);
        provider.setName(new LocalizedText("ФК Звезда", "FC Zvezda"));
        provider.setDescription(new LocalizedText("Футбольная школа", null));
        provider.setRegion(minsk);
        provider.setAddress(new Address(null, "Минск", "ул. Спортивная", "1"));
        provider.setWorkingHours(new LocalizedText("Пн–Пт 9:00–18:00", null));
        provider.setContactEmail("club@example.by");
        provider.getSports().add(football);
        providers.save(provider);
    }

    @Test
    void guestCanSearchAndSeeDictionaries() throws Exception {
        mvc.perform(get("/api/v1/events")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/regions").header("Accept-Language", "en")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Brest Region")));
    }

    @Test
    void cardGoesFromDraftThroughModerationToPublicSearch() throws Exception {
        long eventId = createDraft(eventJson("Турнир по мини-футболу", "+375291234567", "org@example.by"));

        // Черновик не виден гостю
        mvc.perform(get("/api/v1/events/{id}", eventId)).andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/provider/events/{id}/submit", eventId).with(as("provider@example.by")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_MODERATION"));

        mvc.perform(get("/api/v1/moderation/events").with(as("moderator@example.by")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].eventId", hasItem((int) eventId)));
        mvc.perform(get("/api/v1/moderation/events/{id}", eventId).with(as("moderator@example.by")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvedVersion").isEmpty())
                .andExpect(jsonPath("$.submittedVersion.title.ru").value("Турнир по мини-футболу"))
                .andExpect(jsonPath("$.changedFields", hasItems("title", "startsAt")));

        mvc.perform(post("/api/v1/moderation/events/{id}/approve", eventId).with(as("moderator@example.by")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // Регистронезависимый поиск по частичному совпадению (п. 4.7.4) + фильтры (п. 4.7.2)
        mvc.perform(get("/api/v1/events")
                        .param("q", "МИНИ-ФУТ")
                        .param("city", "минск")
                        .param("sportId", football.getId().toString())
                        .param("age", "12")
                        .param("priceMax", "30")
                        .param("hasFreeSeats", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Турнир по мини-футболу"))
                .andExpect(jsonPath("$.content[0].organizer").value("ФК Звезда"))
                .andExpect(jsonPath("$.content[0].minPrice").value(25.00))
                .andExpect(jsonPath("$.content[0].currency").value("BYN"));
        // Поиск по названию вида спорта (п. 4.7.1)
        mvc.perform(get("/api/v1/events").param("q", "футбол"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/events").param("age", "40"))
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/v1/events/{id}", eventId).header("Accept-Language", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Mini-football tournament"))
                // Английского описания нет — используется русское (п. 5.8)
                .andExpect(jsonPath("$.description").value("Открытый турнир для детских команд"))
                .andExpect(jsonPath("$.freeSeats").value(20))
                .andExpect(jsonPath("$.offers[0].currency").value("BYN"))
                .andExpect(jsonPath("$.registrationOpen").value(true));
    }

    @Test
    void publishedVersionStaysPublicWhileEssentialChangesAreModerated() throws Exception {
        long eventId = publish(eventJson("Турнир", "+375291234567", null));

        // Контакты — несущественное изменение: применяется сразу
        mvc.perform(put("/api/v1/provider/events/{id}", eventId).with(as("provider@example.by"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("Турнир", "+375297654321", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.contactPhone").value("+375297654321"))
                .andExpect(jsonPath("$.revision").isEmpty());

        // Название — существенное изменение (п. 4.14.1): уходит на модерацию, карточка остается опубликованной
        mvc.perform(put("/api/v1/provider/events/{id}", eventId).with(as("provider@example.by"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("Кубок города", "+375297654321", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.title.ru").value("Турнир"))
                .andExpect(jsonPath("$.revision.status").value("ON_MODERATION"))
                .andExpect(jsonPath("$.revision.statusLabel").value("На модерации"))
                .andExpect(jsonPath("$.revision.proposedVersion.title.ru").value("Кубок города"));

        // Гость до одобрения видит прежнюю версию
        mvc.perform(get("/api/v1/events/{id}", eventId))
                .andExpect(jsonPath("$.title").value("Турнир"));

        // Повторные правки до решения модератора запрещены
        mvc.perform(put("/api/v1/provider/events/{id}", eventId).with(as("provider@example.by"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("Кубок области", "+375297654321", null)))
                .andExpect(status().isConflict());

        // Diff-режим сравнивает с действующей публичной версией
        mvc.perform(get("/api/v1/moderation/events").with(as("moderator@example.by")))
                .andExpect(jsonPath("$.content[0].eventId").value((int) eventId))
                .andExpect(jsonPath("$.content[0].revision").value(true));
        mvc.perform(get("/api/v1/moderation/events/{id}", eventId).with(as("moderator@example.by")))
                .andExpect(jsonPath("$.approvedVersion.title.ru").value("Турнир"))
                .andExpect(jsonPath("$.submittedVersion.title.ru").value("Кубок города"))
                // Контакты уже применены к публичной версии и в отличия не попадают
                .andExpect(jsonPath("$.changedFields", hasItem("title")))
                .andExpect(jsonPath("$.changedFields", not(hasItem("contactPhone"))));

        mvc.perform(post("/api/v1/moderation/events/{id}/approve", eventId).with(as("moderator@example.by")))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(get("/api/v1/events/{id}", eventId))
                .andExpect(jsonPath("$.title").value("Кубок города"))
                .andExpect(jsonPath("$.offers.length()").value(2));
        mvc.perform(get("/api/v1/provider/events/{id}", eventId).with(as("provider@example.by")))
                .andExpect(jsonPath("$.revision").isEmpty());
    }

    @Test
    void rejectedRevisionKeepsPublicVersionAndNeedsRevisionForAuthor() throws Exception {
        long eventId = publish(eventJson("Турнир", "+375291234567", null));
        mvc.perform(put("/api/v1/provider/events/{id}", eventId).with(as("provider@example.by"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("Кубок города", "+375291234567", null)));

        mvc.perform(post("/api/v1/moderation/events/{id}/reject", eventId).with(as("moderator@example.by"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Название вводит в заблуждение\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mvc.perform(get("/api/v1/events/{id}", eventId))
                .andExpect(jsonPath("$.title").value("Турнир"));
        mvc.perform(get("/api/v1/provider/events").with(as("provider@example.by")))
                .andExpect(jsonPath("$.content[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.content[0].revision.status").value("REJECTED"))
                .andExpect(jsonPath("$.content[0].revision.statusLabel").value("Требует доработки"))
                .andExpect(jsonPath("$.content[0].revision.rejectionReason").value("Название вводит в заблуждение"));

        // После отклонения автор может прислать исправленные правки
        mvc.perform(put("/api/v1/provider/events/{id}", eventId).with(as("provider@example.by"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("Кубок Минска", "+375291234567", null)))
                .andExpect(jsonPath("$.revision.status").value("ON_MODERATION"));
    }

    @Test
    void rejectionRequiresReasonAndIsShownToAuthor() throws Exception {
        long eventId = createDraft(eventJson("Турнир", "+375291234567", null));
        mvc.perform(post("/api/v1/provider/events/{id}/submit", eventId).with(as("provider@example.by")));

        mvc.perform(post("/api/v1/moderation/events/{id}/reject", eventId).with(as("moderator@example.by"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"  \"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("reason"));

        mvc.perform(post("/api/v1/moderation/events/{id}/reject", eventId).with(as("moderator@example.by"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Нет программы турнира\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // В БД — «Отклонена», автору — «Требует доработки» (решение PM)
        mvc.perform(get("/api/v1/provider/events").with(as("provider@example.by")))
                .andExpect(jsonPath("$.content[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.content[0].statusLabel").value("Требует доработки"))
                .andExpect(jsonPath("$.content[0].rejectionReason").value("Нет программы турнира"));
        mvc.perform(get("/api/v1/provider/events").with(as("provider@example.by")).header("Accept-Language", "en"))
                .andExpect(jsonPath("$.content[0].statusLabel").value("Needs revision"));
    }

    @Test
    void invalidContactsAreRejectedWith422() throws Exception {
        mvc.perform(post("/api/v1/provider/events").with(as("provider@example.by"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("Турнир", "80291234567", "not-an-email")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[*].field", hasItems("contactPhone", "contactEmail")));

        mvc.perform(post("/api/v1/provider/events").with(as("provider@example.by"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("Турнир", null, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[*].field", hasItem("contactPresent")));
    }

    @Test
    void roleMatrixIsEnforced() throws Exception {
        String body = eventJson("Турнир", "+375291234567", null);
        mvc.perform(post("/api/v1/provider/events").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/provider/events").with(as("user@example.by"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/moderation/events").with(as("provider@example.by")))
                .andExpect(status().isForbidden());
        // Изменяющий запрос без CSRF-токена отклоняется даже при действующей сессии (п. 4.13.5)
        mvc.perform(post("/api/v1/provider/events").with(user("provider@example.by").roles("PROVIDER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void uploadedFilesAreValidatedBySizeExtensionAndContent() throws Exception {
        long eventId = createDraft(eventJson("Турнир", "+375291234567", null));

        byte[] tinyJpeg = jpeg(10 * 1024);
        mvc.perform(multipart("/api/v1/provider/events/{id}/images", eventId)
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", tinyJpeg))
                        .with(as("provider@example.by")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("file"));

        mvc.perform(multipart("/api/v1/provider/events/{id}/images", eventId)
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[200 * 1024]))
                        .with(as("provider@example.by")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].message").value("Содержимое файла не соответствует его формату"));

        mvc.perform(multipart("/api/v1/provider/events/{id}/documents", eventId)
                        .file(new MockMultipartFile("file", "rules.pdf", "application/pdf", new byte[1024]))
                        .with(as("provider@example.by")))
                .andExpect(status().isUnprocessableEntity());

        // Валидный файл проходит проверку; хранилище еще не реализовано
        mvc.perform(multipart("/api/v1/provider/events/{id}/images", eventId)
                        .file(new MockMultipartFile("file", "photo.JPG", "image/jpeg", jpeg(200 * 1024)))
                        .with(as("provider@example.by")))
                .andExpect(status().isNotImplemented());

        mvc.perform(post("/api/v1/provider/events/{id}/videos", eventId).with(as("provider@example.by"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"url\": \"https://youtube.com/watch?v=1\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void swaggerDocumentationIsPublic() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("FindSport API"))
                .andExpect(jsonPath("$.paths['/api/v1/events']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/moderation/events/{id}/reject']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.sessionCookie.name").value("JSESSIONID"));
    }

    private long publish(String body) throws Exception {
        long eventId = createDraft(body);
        mvc.perform(post("/api/v1/provider/events/{id}/submit", eventId).with(as("provider@example.by")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/moderation/events/{id}/approve", eventId).with(as("moderator@example.by")))
                .andExpect(status().isOk());
        return eventId;
    }

    private long createDraft(String body) throws Exception {
        String response = mvc.perform(post("/api/v1/provider/events").with(as("provider@example.by"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    private String eventJson(String titleRu, String phone, String email) throws Exception {
        var body = json.createObjectNode()
                .put("providerId", provider.getId())
                .put("type", "TOURNAMENT")
                .put("startsAt", start.toString())
                .put("endsAt", start.plusHours(4).toString())
                .put("regionId", minsk.getId())
                .put("ageMin", 10)
                .put("ageMax", 14)
                .put("participantLimit", 20)
                .put("contactPhone", phone)
                .put("contactEmail", email);
        body.putObject("title").put("ru", titleRu).put("en", "Mini-football tournament");
        body.putObject("description").put("ru", "Открытый турнир для детских команд");
        body.putObject("conditions").put("ru", "Команды до 8 человек");
        body.putArray("sportIds").add(football.getId());
        body.putObject("address").put("locality", "Минск").put("street", "ул. Спортивная").put("building", "1");
        var offers = body.putArray("offers");
        offers.addObject().put("price", "25.00").putObject("title").put("ru", "Участник");
        offers.addObject().put("price", "40.00").putObject("title").put("ru", "Участник с экипировкой");
        return json.writeValueAsString((JsonNode) body);
    }

    private User createUser(String email, UserRole role) {
        User user = new User(email, passwordEncoder.encode(PASSWORD), role);
        user.setEmailVerified(true);
        return users.save(user);
    }

    /** Аутентифицированная сессия с ролью из БД и CSRF-токеном. */
    private RequestPostProcessor as(String email) {
        String role = users.findByEmail(email).orElseThrow().getRole().name();
        return request -> csrf().postProcessRequest(user(email).roles(role).postProcessRequest(request));
    }

    private static byte[] jpeg(int size) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) 1);
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return bytes;
    }
}
