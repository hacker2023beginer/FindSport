package by.findsport.config;

import by.findsport.domain.UserRole;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Аутентификация и доступ к API по матрице ролей (ТЗ Приложение 2, п. 4.13.1).
 * <ul>
 *     <li>Вход — {@code POST /api/v1/auth/login}, далее серверная сессия в cookie {@code JSESSIONID}
 *     (HttpOnly, SameSite=Lax). Выход — {@code POST /api/v1/auth/logout} закрывает сессию.</li>
 *     <li>CSRF (п. 4.13.5): токен в cookie {@code XSRF-TOKEN}, каждый изменяющий запрос передает его
 *     в заголовке {@code X-XSRF-TOKEN}. Токен выдается с первым ответом и меняется при входе.</li>
 *     <li>Административная сессия ограничена 30 минутами неактивности (п. 4.2.4) — см. {@link SessionAuthenticator}.</li>
 * </ul>
 * Полномочия проверяются только на сервере: скрытие кнопок в интерфейсе не является защитой.
 */
@Configuration
@EnableConfigurationProperties({FileUploadProperties.class, AuthProperties.class})
public class SecurityConfig {

    private static final String USER = UserRole.USER.name();
    private static final String PROVIDER = UserRole.PROVIDER.name();
    private static final String MODERATOR = UserRole.MODERATOR.name();
    private static final String ADMIN = UserRole.ADMIN.name();

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, CookieCsrfTokenRepository csrfTokens,
                                           SecurityContextRepository contexts,
                                           SecurityProblemHandlers problems,
                                           PublicFormRateLimitFilter rateLimit) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokens)
                        .csrfTokenRequestHandler(plainCsrfTokenHandler()))
                .securityContext(context -> context.securityContextRepository(contexts))
                .requestCache(RequestCacheConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Гость: просмотр публикаций, поиск, справочники для фильтров
                        .requestMatchers(HttpMethod.GET, "/api/v1/events", "/api/v1/events/*",
                                "/api/v1/regions", "/api/v1/sports").permitAll()
                        // Публичные формы: регистрация, подтверждение e-mail, вход, восстановление доступа
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/email-verification", "/api/v1/auth/email-verification/resend",
                                "/api/v1/auth/password/forgot", "/api/v1/auth/password/reset").permitAll()
                        // Личный кабинет — любая авторизованная роль
                        .requestMatchers("/api/v1/auth/me", "/api/v1/account/**")
                        .hasAnyRole(USER, PROVIDER, MODERATOR, ADMIN)
                        // Управление своими карточками: поставщик; модератор* и администратор
                        .requestMatchers("/api/v1/provider/**").hasAnyRole(PROVIDER, MODERATOR, ADMIN)
                        .requestMatchers("/api/v1/moderation/**").hasAnyRole(MODERATOR, ADMIN)
                        // Управление ролями и учетными записями — только администратор
                        .requestMatchers("/api/v1/admin/**").hasRole(ADMIN)
                        // Интерактивная документация API (п. 4.1.3, 4.4.5)
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Страницы и статика веб-интерфейса; данные защищает API
                        .requestMatchers(HttpMethod.GET, "/", "/*.html", "/css/**", "/js/**", "/favicon.ico")
                        .permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .addLogoutHandler(problems)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .addFilterBefore(rateLimit, CsrfFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /** Фильтр работает только внутри цепочки безопасности, а не повторно на уровне контейнера. */
    @Bean
    public FilterRegistrationBean<PublicFormRateLimitFilter> rateLimitFilterRegistration(
            PublicFormRateLimitFilter filter) {
        FilterRegistrationBean<PublicFormRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /** Cookie читает JavaScript веб-интерфейса и возвращает значение в заголовке {@code X-XSRF-TOKEN}. */
    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return repository;
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** При входе — новый идентификатор сессии (защита от фиксации сессии) и новый CSRF-токен. */
    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy(CookieCsrfTokenRepository csrfTokens) {
        CsrfAuthenticationStrategy csrf = new CsrfAuthenticationStrategy(csrfTokens);
        csrf.setRequestHandler(plainCsrfTokenHandler());
        return new CompositeSessionAuthenticationStrategy(List.of(new ChangeSessionIdAuthenticationStrategy(), csrf));
    }

    /** BCrypt — стойкий хэш с индивидуальной солью (п. 4.13.2). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Значение сравнивается без маскирования: веб-интерфейс передает его из cookie как есть. */
    private static CsrfTokenRequestAttributeHandler plainCsrfTokenHandler() {
        return new CsrfTokenRequestAttributeHandler();
    }
}
