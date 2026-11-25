package by.findsport.tools;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Настройки генератора синтетических данных (план недели 5, задача разработчика 7).
 * Генератор включается только явным признаком {@code findsport.tools.seed.enabled=true};
 * по умолчанию выключен.
 *
 * @param events    число создаваемых мероприятий
 * @param providers число создаваемых профилей поставщиков
 * @param password  пароль учетных записей поставщиков
 */
@ConfigurationProperties(prefix = "findsport.tools.seed")
public record SeedProperties(boolean enabled, int events, int providers, String password) {

    public SeedProperties {
        if (events < 0) {
            events = 0;
        }
        if (providers < 1) {
            providers = 1;
        }
        if (password == null || password.isBlank()) {
            password = "Seed-123";
        }
    }
}
