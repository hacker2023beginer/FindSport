package by.findsport.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Документированный версионированный API (ТЗ п. 4.1.3, 4.4.5): спецификация OpenAPI 3 на
 * {@code /v3/api-docs}, интерактивный Swagger UI на {@code /swagger-ui.html}.
 */
@Configuration
public class OpenApiConfig {

    /** Имя схемы авторизации для {@code @SecurityRequirement} на закрытых контроллерах. */
    public static final String SESSION_AUTH = "sessionCookie";

    @Bean
    public OpenAPI findSportOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FindSport API")
                        .version("v1")
                        .description("""
                                API платформы поиска спортивных мероприятий Республики Беларусь.
                                Язык ответа — заголовок Accept-Language (ru по умолчанию, en).
                                Даты — Europe/Minsk, цены — BYN. Ошибки — RFC 7807, валидация — 422.
                                Вход — POST /api/v1/auth/login (сессия в cookie JSESSIONID). Изменяющие запросы
                                передают CSRF-токен из cookie XSRF-TOKEN в заголовке X-XSRF-TOKEN."""))
                .components(new Components().addSecuritySchemes(SESSION_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("JSESSIONID")
                        .description("Серверная сессия после POST /api/v1/auth/login")));
    }
}
