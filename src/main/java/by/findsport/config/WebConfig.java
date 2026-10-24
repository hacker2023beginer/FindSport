package by.findsport.config;

import java.util.List;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Язык ответа API выбирается заголовком {@code Accept-Language} (ТЗ п. 4.1.6).
 * Без заголовка или для неподдерживаемого языка используется русский, независимо от локали сервера.
 */
@Configuration
public class WebConfig {

    private static final Locale RUSSIAN = Locale.forLanguageTag("ru");

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(RUSSIAN, Locale.ENGLISH));
        resolver.setDefaultLocale(RUSSIAN);
        return resolver;
    }
}
