package by.findsport.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import by.findsport.TestcontainersConfiguration;
import by.findsport.domain.User;
import by.findsport.domain.UserRole;
import by.findsport.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/**
 * CSRF-защита (ТЗ п. 4.13.5) так, как ее использует веб-интерфейс: токен из cookie {@code XSRF-TOKEN}
 * возвращается в заголовке {@code X-XSRF-TOKEN}.
 * <p>
 * Отдельный контекст Spring: тестовый {@code csrf()} из spring-security-test подменяет хранилище токенов
 * в общем контексте, а здесь проверяется настоящее.
 */
@SpringBootTest(properties = "findsport.test.context=real-csrf")
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class CsrfCookieFlowIntegrationTests {

    private static final String PASSWORD = "Secret-123";
    private static final String LOGIN = "{\"email\": \"user@example.by\", \"password\": \"" + PASSWORD + "\"}";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void csrfTokenFromCookieProtectsStateChangingRequests() throws Exception {
        User user = new User("user@example.by", passwordEncoder.encode(PASSWORD), UserRole.USER);
        user.setEmailVerified(true);
        users.save(user);

        // Первый ответ выдает токен в cookie, доступной JavaScript
        MvcResult first = mvc.perform(get("/api/v1/regions"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andReturn();
        Cookie xsrf = first.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrf.getAttribute("SameSite")).isEqualTo("Lax");

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isForbidden());

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isOk())
                .andReturn();
        // При входе токен меняется: ответ удаляет прежнюю cookie и выдает новую
        Cookie rotated = Arrays.stream(login.getResponse().getCookies())
                .filter(c -> c.getName().equals("XSRF-TOKEN") && !c.getValue().isEmpty())
                .reduce((previous, next) -> next)
                .orElseThrow();
        assertThat(rotated.getValue()).isNotEqualTo(xsrf.getValue());
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mvc.perform(post("/api/v1/auth/logout").session(session).cookie(rotated)
                        .header("X-XSRF-TOKEN", xsrf.getValue()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/logout").session(session).cookie(rotated)
                        .header("X-XSRF-TOKEN", rotated.getValue()))
                .andExpect(status().isNoContent());
    }
}
