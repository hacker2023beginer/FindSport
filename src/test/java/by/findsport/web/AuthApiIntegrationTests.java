package by.findsport.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import by.findsport.CapturingMailSender;
import by.findsport.TestcontainersConfiguration;
import by.findsport.config.PublicFormRateLimitFilter;
import by.findsport.domain.LocalizedText;
import by.findsport.domain.Region;
import by.findsport.domain.Sport;
import by.findsport.domain.User;
import by.findsport.domain.UserRole;
import by.findsport.repository.RegionRepository;
import by.findsport.repository.SportRepository;
import by.findsport.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Регистрация, вход и права доступа (неделя 3) на реальном PostgreSQL: приемочный сценарий
 * «регистрация → подтверждение → вход → профиль» (ТЗ Приложение 3), ограничение неуспешных входов (п. 4.2.3),
 * восстановление доступа, завершение сессии, матрица ролей (Приложение 2),
 * частота публичных форм (п. 4.2.14) и недоступность почты (п. 4.2.16).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class AuthApiIntegrationTests {

    private static final String PASSWORD = "Secret-123";
    /** Разные адреса клиентов, чтобы серии входов не упирались в ограничение частоты форм. */
    private static final AtomicInteger CLIENT = new AtomicInteger();

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
    private CapturingMailSender mail;
    @Autowired
    private PublicFormRateLimitFilter rateLimit;

    @BeforeEach
    void setUp() {
        mail.reset();
        rateLimit.reset();
    }

    @Test
    void userRegistersConfirmsEmailLogsInAndEditsProfile() throws Exception {
        mvc.perform(post("/api/v1/auth/register").with(csrf()).header("Accept-Language", "ru")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("Maria@Example.by", PASSWORD, "USER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").isNotEmpty());

        User created = users.findByEmail("maria@example.by").orElseThrow();
        assertThat(created.getRole()).isEqualTo(UserRole.USER);
        assertThat(created.isEmailVerified()).isFalse();
        assertThat(created.getConsentsAcceptedAt()).isNotNull();
        // Пароль хранится только стойким хэшем с солью (п. 4.13.2)
        assertThat(created.getPasswordHash()).startsWith("$2").doesNotContain(PASSWORD);
        assertThat(mail.sent()).singleElement().satisfies(message -> {
            assertThat(message.getTo()).containsExactly("maria@example.by");
            assertThat(message.getSubject()).contains("подтвердите e-mail");
            assertThat(message.getText()).contains("http://localhost:8080/verify-email.html?token=");
        });

        // До подтверждения адреса вход невозможен (п. 4.2.2)
        login("maria@example.by", PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value("EMAIL_NOT_VERIFIED"));

        String token = mail.lastTokenFor("maria@example.by");
        mvc.perform(post("/api/v1/auth/email-verification").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("token", token))))
                .andExpect(status().isOk());
        // Ссылка одноразовая
        mvc.perform(post("/api/v1/auth/email-verification").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("token", token))))
                .andExpect(status().isBadRequest());

        MockHttpSession session = signIn("maria@example.by", PASSWORD);
        mvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("maria@example.by"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.displayName").value("Мария"));

        Region minsk = regions.findAll().stream().filter(r -> r.getCode().equals("MINSK")).findFirst().orElseThrow();
        Sport running = sports.save(new Sport("RUNNING", new LocalizedText("Бег", "Running")));
        var profile = json.createObjectNode()
                .put("displayName", "Мария Соколова")
                .put("phone", "+375297654321")
                .put("regionId", minsk.getId())
                .put("preferredLanguage", "EN");
        profile.putArray("sportIds").add(running.getId());
        mvc.perform(put("/api/v1/account/profile").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(profile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Мария Соколова"))
                .andExpect(jsonPath("$.region.name").value("г. Минск"))
                .andExpect(jsonPath("$.sportInterests[0].name").value("Бег"))
                .andExpect(jsonPath("$.preferredLanguage").value("EN"));

        mvc.perform(put("/api/v1/account/settings").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("personalizationConsent", true, "emailNotifications", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalizationConsent").value(true))
                .andExpect(jsonPath("$.emailNotifications").value(false));

        mvc.perform(get("/api/v1/account/profile").session(session).header("Accept-Language", "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("maria@example.by"))
                .andExpect(jsonPath("$.phone").value("+375297654321"))
                .andExpect(jsonPath("$.region.name").value("Minsk"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.consentsAcceptedAt").isNotEmpty());

        // Завершение сессии: после выхода закрытые функции недоступны (ТЗ п. 6.7, «истекшая сессия»)
        mvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/v1/account/profile").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationValidatesInputAndConsents() throws Exception {
        var invalid = json.createObjectNode()
                .put("email", "not-an-email")
                .put("password", "short")
                .put("accountType", "USER")
                .put("termsAccepted", false)
                .put("privacyPolicyAccepted", false);
        mvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(invalid)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[*].field",
                        hasItems("email", "password", "termsAccepted", "privacyPolicyAccepted")));

        // Модератора и администратора назначает только администратор (п. 4.5.2)
        mvc.perform(post("/api/v1/auth/register").with(csrf()).with(fromNewClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("boss@example.by", PASSWORD, "ADMIN")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("accountTypeAllowed"));

        createUser("taken@example.by", UserRole.USER);
        mvc.perform(post("/api/v1/auth/register").with(csrf()).with(fromNewClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("TAKEN@example.by", PASSWORD, "USER")))
                .andExpect(status().isConflict());

        // Представитель организации или тренер регистрируется сам (п. 3.2.2)
        mvc.perform(post("/api/v1/auth/register").with(csrf()).with(fromNewClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("coach@example.by", PASSWORD, "PROVIDER")))
                .andExpect(status().isCreated());
        assertThat(users.findByEmail("coach@example.by").orElseThrow().getRole()).isEqualTo(UserRole.PROVIDER);
    }

    @Test
    void fiveFailedLoginsTemporarilyLockAccountAndAreLogged(CapturedOutput output) throws Exception {
        createUser("victim@example.by", UserRole.USER);

        for (int attempt = 1; attempt < 5; attempt++) {
            login("victim@example.by", "Wrong-pass1")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.reason").value("BAD_CREDENTIALS"))
                    .andExpect(jsonPath("$.detail").value("Неверный e-mail или пароль"));
        }
        login("victim@example.by", "Wrong-pass1")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.reason").value("LOCKED"))
                .andExpect(jsonPath("$.lockedUntil").isNotEmpty());

        // Пока действует ограничение, не проходит и верный пароль
        login("victim@example.by", PASSWORD).andExpect(status().isLocked());
        User victim = users.findByEmail("victim@example.by").orElseThrow();
        assertThat(victim.getLockedUntil()).isNotNull();

        // Событие безопасности зафиксировано без пароля и e-mail (п. 4.2.3, 4.13.13)
        var securityLog = output.getAll().lines().filter(line -> line.contains("by.findsport.security")).toList();
        assertThat(securityLog).anyMatch(line -> line.contains("ACCOUNT_LOCKED userId=" + victim.getId()));
        assertThat(securityLog).noneMatch(line -> line.contains("Wrong-pass1") || line.contains("victim@example.by"));

        // Неизвестный e-mail дает тот же ответ, что и неверный пароль
        login("nobody@example.by", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Неверный e-mail или пароль"));
    }

    @Test
    void successfulLoginResetsFailureCounter() throws Exception {
        createUser("forgetful@example.by", UserRole.USER);
        for (int attempt = 0; attempt < 4; attempt++) {
            login("forgetful@example.by", "Wrong-pass1").andExpect(status().isUnauthorized());
        }
        login("forgetful@example.by", PASSWORD).andExpect(status().isOk());
        assertThat(users.findByEmail("forgetful@example.by").orElseThrow().getFailedLoginAttempts()).isZero();
        // Счет начинается заново — одна ошибка не блокирует
        login("forgetful@example.by", "Wrong-pass1").andExpect(status().isUnauthorized());
    }

    @Test
    void accessIsRestoredByEmailLinkAndLockIsCleared() throws Exception {
        createUser("lost@example.by", UserRole.USER);
        for (int attempt = 0; attempt < 5; attempt++) {
            login("lost@example.by", "Wrong-pass1");
        }
        login("lost@example.by", PASSWORD).andExpect(status().isLocked());

        // Ответ не раскрывает, зарегистрирован ли адрес
        forgot("unknown@example.by").andExpect(status().isAccepted());
        assertThat(mail.sent()).isEmpty();
        forgot("lost@example.by").andExpect(status().isAccepted());
        String token = mail.lastTokenFor("lost@example.by");
        assertThat(mail.sent().getLast().getText()).contains("/reset-password.html?token=");

        mvc.perform(post("/api/v1/auth/password/reset").with(csrf()).with(fromNewClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("token", token, "newPassword", "12345678"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));
        mvc.perform(post("/api/v1/auth/password/reset").with(csrf()).with(fromNewClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("token", token, "newPassword", "New-pass-42"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/password/reset").with(csrf()).with(fromNewClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("token", token, "newPassword", "Other-pass-42"))))
                .andExpect(status().isBadRequest());

        login("lost@example.by", PASSWORD).andExpect(status().isUnauthorized());
        login("lost@example.by", "New-pass-42").andExpect(status().isOk());
    }

    @Test
    void passwordChangeRequiresCurrentPassword() throws Exception {
        createUser("owner@example.by", UserRole.USER);
        MockHttpSession session = signIn("owner@example.by", PASSWORD);

        mvc.perform(put("/api/v1/account/password").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", "Wrong-pass1", "newPassword", "New-pass-42"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("currentPassword"));
        mvc.perform(put("/api/v1/account/password").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("currentPassword", PASSWORD, "newPassword", "New-pass-42"))))
                .andExpect(status().isOk());

        login("owner@example.by", "New-pass-42").andExpect(status().isOk());
    }

    @Test
    void accountDeletionRequestIsRecorded() throws Exception {
        createUser("leaving@example.by", UserRole.USER);
        MockHttpSession session = signIn("leaving@example.by", PASSWORD);

        mvc.perform(delete("/api/v1/account").session(session).with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.deletionRequestedAt").isNotEmpty());
        assertThat(users.findByEmail("leaving@example.by").orElseThrow().getDeletionRequestedAt()).isNotNull();
    }

    @Test
    void profileEndpointsOnlyTouchOwnAccount() throws Exception {
        createUser("alice@example.by", UserRole.USER);
        createUser("bob@example.by", UserRole.USER);
        MockHttpSession alice = signIn("alice@example.by", PASSWORD);

        var profile = json.createObjectNode().put("displayName", "Алиса").put("preferredLanguage", "RU");
        profile.putArray("sportIds");
        mvc.perform(put("/api/v1/account/profile").session(alice).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(profile)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.by"));

        assertThat(users.findByEmail("bob@example.by").orElseThrow().getDisplayName()).isNull();
        // Реестр учетных записей с чужими данными доступен только администратору
        mvc.perform(get("/api/v1/admin/users").session(alice)).andExpect(status().isForbidden());
    }

    @Test
    void roleMatrixIsEnforcedOnServer() throws Exception {
        createUser("user@example.by", UserRole.USER);
        createUser("provider@example.by", UserRole.PROVIDER);
        createUser("moderator@example.by", UserRole.MODERATOR);
        createUser("admin@example.by", UserRole.ADMIN);

        // Гость: публичные разделы доступны, личный кабинет — нет
        mvc.perform(get("/api/v1/events")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/account/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        MockHttpSession user = signIn("user@example.by", PASSWORD);
        mvc.perform(get("/api/v1/account/profile").session(user)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/provider/events").session(user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/moderation/events").session(user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/users").session(user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Недостаточно прав для выполнения операции"));

        MockHttpSession provider = signIn("provider@example.by", PASSWORD);
        mvc.perform(get("/api/v1/provider/events").session(provider)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/moderation/events").session(provider)).andExpect(status().isForbidden());

        MockHttpSession moderator = signIn("moderator@example.by", PASSWORD);
        mvc.perform(get("/api/v1/moderation/events").session(moderator)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/users").session(moderator)).andExpect(status().isForbidden());

        MockHttpSession admin = signIn("admin@example.by", PASSWORD);
        mvc.perform(get("/api/v1/admin/users").session(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/moderation/events").session(admin)).andExpect(status().isOk());
    }

    @Test
    void administrativeSessionExpiresAfterThirtyMinutesOfInactivity() throws Exception {
        createUser("admin@example.by", UserRole.ADMIN);
        createUser("moderator@example.by", UserRole.MODERATOR);
        createUser("user@example.by", UserRole.USER);

        assertThat(signIn("admin@example.by", PASSWORD).getMaxInactiveInterval()).isEqualTo(30 * 60);
        assertThat(signIn("moderator@example.by", PASSWORD).getMaxInactiveInterval()).isEqualTo(30 * 60);
        assertThat(signIn("user@example.by", PASSWORD).getMaxInactiveInterval()).isNotEqualTo(30 * 60);
    }

    @Test
    void adminManagesRolesAndBlocking() throws Exception {
        User admin = createUser("admin@example.by", UserRole.ADMIN);
        User target = createUser("member@example.by", UserRole.USER);
        MockHttpSession session = signIn("admin@example.by", PASSWORD);

        mvc.perform(put("/api/v1/admin/users/{id}/role", target.getId()).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("role", "MODERATOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MODERATOR"));
        login("member@example.by", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MODERATOR"));

        mvc.perform(post("/api/v1/admin/users/{id}/block", target.getId()).session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true));
        login("member@example.by", PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.reason").value("BLOCKED"));
        // Восстановление доступа не обходит блокировку администратором
        forgot("member@example.by").andExpect(status().isAccepted());
        assertThat(mail.sent()).isEmpty();

        mvc.perform(post("/api/v1/admin/users/{id}/unblock", target.getId()).session(session).with(csrf()))
                .andExpect(status().isOk());
        login("member@example.by", PASSWORD).andExpect(status().isOk());

        // Администратор не может лишить себя роли
        mvc.perform(put("/api/v1/admin/users/{id}/role", admin.getId()).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("role", "USER"))))
                .andExpect(status().isConflict());
    }

    @Test
    void publicFormsAreRateLimited() throws Exception {
        RequestPostProcessor client = fromNewClient();
        for (int request = 0; request < 5; request++) {
            mvc.perform(post("/api/v1/auth/password/forgot").with(csrf()).with(client)
                            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", "a@example.by"))))
                    .andExpect(status().isAccepted());
        }
        mvc.perform(post("/api/v1/auth/password/forgot").with(csrf()).with(client)
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", "a@example.by"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3600"))
                .andExpect(jsonPath("$.detail").value(
                        "Слишком много запросов. Форма временно заблокирована, повторите попытку через 60 мин."));

        // Другие клиенты и другие формы не затронуты
        mvc.perform(post("/api/v1/auth/password/forgot").with(csrf()).with(fromNewClient())
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", "a@example.by"))))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/v1/auth/login").with(csrf()).with(client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("email", "a@example.by", "password", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationSucceedsWhenMailIsUnavailable(CapturedOutput output) throws Exception {
        mail.setUnavailable(true);
        mvc.perform(post("/api/v1/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("offline@example.by", PASSWORD, "USER")))
                .andExpect(status().isCreated());
        assertThat(users.findByEmail("offline@example.by")).isPresent();
        assertThat(output).contains("Почтовый сервис недоступен");

        // Когда почта снова работает, письмо можно запросить повторно
        mail.setUnavailable(false);
        mvc.perform(post("/api/v1/auth/email-verification/resend").with(csrf()).with(fromNewClient())
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", "offline@example.by"))))
                .andExpect(status().isAccepted());
        assertThat(mail.lastTokenFor("offline@example.by")).isNotBlank();
    }

    @Test
    void verificationEmailUsesPreferredLanguage() throws Exception {
        mvc.perform(post("/api/v1/auth/register").with(csrf()).header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registration("john@example.by", PASSWORD, "USER")))
                .andExpect(status().isCreated());
        assertThat(mail.sent().getLast().getSubject()).isEqualTo("FindSport: confirm your e-mail");
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").with(csrf()).with(fromNewClient())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email, "password", password))));
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        MvcResult result = login(email, password).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private ResultActions forgot(String email) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/auth/password/forgot").with(csrf()).with(fromNewClient())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email)));
        return mvc.perform(request);
    }

    private String registration(String email, String password, String accountType) throws Exception {
        return body(Map.of(
                "email", email,
                "password", password,
                "displayName", "Мария",
                "accountType", accountType,
                "termsAccepted", true,
                "privacyPolicyAccepted", true));
    }

    private String body(Map<String, ?> values) throws Exception {
        return json.writeValueAsString(values);
    }

    private User createUser(String email, UserRole role) {
        User user = new User(email, passwordEncoder.encode(PASSWORD), role);
        user.setEmailVerified(true);
        return users.save(user);
    }

    private static RequestPostProcessor fromNewClient() {
        String address = "10.0." + (CLIENT.get() / 250) % 250 + "." + (CLIENT.incrementAndGet() % 250 + 1);
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
