package by.findsport.config;

import by.findsport.service.UserAdministrationService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Создает первичного администратора, если заданы {@code ADMIN_EMAIL} и {@code ADMIN_PASSWORD}
 * (секреты только через окружение, ТЗ п. 4.13.8, 6.6). Остальные роли назначает администратор.
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserAdministrationService administration;

    @Value("${findsport.bootstrap-admin.email:}")
    private String email;
    @Value("${findsport.bootstrap-admin.password:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (StringUtils.hasText(email) && StringUtils.hasText(password)) {
            administration.ensureAdministrator(email.trim().toLowerCase(Locale.ROOT), password);
        }
    }
}
