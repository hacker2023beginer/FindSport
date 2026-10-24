package by.findsport.config;

import static org.springframework.security.config.Customizer.withDefaults;

import by.findsport.domain.UserRole;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Доступ к API по матрице ролей (ТЗ Приложение 2, п. 4.13.1).
 * <p>
 * Временная схема: stateless HTTP Basic для каркаса API. На этапе «Авторизация» (этап 3)
 * заменяется на выдачу токенов/сессий с блокировкой после 5 неудачных входов (п. 4.2.3)
 * и 30-минутной админ-сессией (п. 4.2.4); тогда же пересматривается CSRF-защита (п. 4.13.5).
 */
@Configuration
@EnableConfigurationProperties(FileUploadProperties.class)
public class SecurityConfig {

    private static final String PROVIDER = UserRole.PROVIDER.name();
    private static final String MODERATOR = UserRole.MODERATOR.name();
    private static final String ADMIN = UserRole.ADMIN.name();

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Гость: просмотр публикаций, поиск, справочники для фильтров
                        .requestMatchers(HttpMethod.GET, "/api/v1/events", "/api/v1/events/*",
                                "/api/v1/regions", "/api/v1/sports").permitAll()
                        // Управление своими карточками: поставщик; модератор* и администратор
                        .requestMatchers("/api/v1/provider/**").hasAnyRole(PROVIDER, MODERATOR, ADMIN)
                        .requestMatchers("/api/v1/moderation/**").hasAnyRole(MODERATOR, ADMIN)
                        // Интерактивная документация API (п. 4.1.3, 4.4.5)
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /** BCrypt — стойкий хэш с индивидуальной солью (п. 4.13.2). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
