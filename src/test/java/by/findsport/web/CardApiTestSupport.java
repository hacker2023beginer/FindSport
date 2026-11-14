package by.findsport.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import by.findsport.TestcontainersConfiguration;
import by.findsport.domain.Address;
import by.findsport.domain.CardStatus;
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
import org.junit.jupiter.api.BeforeEach;
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
 * Общие данные сценариев с карточками на реальном PostgreSQL: поставщик с подтвержденным профилем,
 * второй поставщик, модератор и пользователь. Каждый тест откатывает свои данные транзакцией.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
abstract class CardApiTestSupport {

    static final String PASSWORD = "Secret-123";
    static final String PROVIDER = "provider@example.by";
    static final String OTHER_PROVIDER = "other-provider@example.by";
    static final String MODERATOR = "moderator@example.by";
    static final String USER = "user@example.by";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    UserRepository users;
    @Autowired
    RegionRepository regions;
    @Autowired
    SportRepository sports;
    @Autowired
    ProviderRepository providers;

    Region minsk;
    Sport football;
    /** Профиль поставщика, подтвержденный модератором. */
    Provider provider;
    /** Одна дата на тест: сдвиг даты — существенное изменение карточки. */
    OffsetDateTime start;

    @BeforeEach
    void setUpCards() {
        start = OffsetDateTime.now(ZoneId.of("Europe/Minsk")).plusDays(10).withNano(0);
        minsk = regions.findAll().stream().filter(r -> r.getCode().equals("MINSK")).findFirst().orElseThrow();
        football = sports.save(new Sport("FOOTBALL", new LocalizedText("Футбол", "Football")));
        User owner = createUser(PROVIDER, UserRole.PROVIDER);
        createUser(OTHER_PROVIDER, UserRole.PROVIDER);
        createUser(MODERATOR, UserRole.MODERATOR);
        createUser(USER, UserRole.USER);
        provider = createProvider(owner, CardStatus.PUBLISHED);
    }

    Provider createProvider(User owner, CardStatus status) {
        Provider created = new Provider(owner, ProviderType.ORGANIZATION);
        created.setName(new LocalizedText("ФК Звезда", "FC Zvezda"));
        created.setDescription(new LocalizedText("Футбольная школа", null));
        created.setRegion(minsk);
        created.setAddress(new Address(null, "Минск", "ул. Спортивная", "1"));
        created.setWorkingHours(new LocalizedText("Пн–Пт 9:00–18:00", null));
        created.setContactEmail("club@example.by");
        created.getSports().add(football);
        created.setStatus(status);
        return providers.save(created);
    }

    long publish(String body) throws Exception {
        long eventId = createDraft(body);
        mvc.perform(post("/api/v1/provider/events/{id}/submit", eventId).with(as(PROVIDER)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/moderation/events/{id}/approve", eventId).with(as(MODERATOR)))
                .andExpect(status().isOk());
        return eventId;
    }

    long createDraft(String body) throws Exception {
        String response = mvc.perform(post("/api/v1/provider/events").with(as(PROVIDER))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }

    String eventJson(String titleRu, String phone, String email) throws Exception {
        return eventJson(provider.getId(), titleRu, phone, email);
    }

    String eventJson(Long providerId, String titleRu, String phone, String email) throws Exception {
        var body = json.createObjectNode()
                .put("providerId", providerId)
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

    User createUser(String email, UserRole role) {
        User user = new User(email, passwordEncoder.encode(PASSWORD), role);
        user.setEmailVerified(true);
        return users.save(user);
    }

    /** Аутентифицированная сессия с ролью из БД и CSRF-токеном. */
    RequestPostProcessor as(String email) {
        String role = users.findByEmail(email).orElseThrow().getRole().name();
        return request -> csrf().postProcessRequest(user(email).roles(role).postProcessRequest(request));
    }

    long idOf(String response) throws Exception {
        return json.readTree(response).get("id").asLong();
    }
}
