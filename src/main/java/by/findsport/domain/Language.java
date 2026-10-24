package by.findsport.domain;

import java.util.Locale;

/**
 * Поддерживаемые языки интерфейса и контента (ТЗ п. 4.1.6).
 */
public enum Language {
    RU,
    EN;

    public static Language from(Locale locale) {
        return locale != null && "en".equalsIgnoreCase(locale.getLanguage()) ? EN : RU;
    }
}
