package by.findsport.web;

import java.util.Locale;

/** Нормализация входных значений перед передачей в сервисы. */
final class Inputs {

    private Inputs() {
    }

    static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** E-mail хранится в нижнем регистре. */
    static String email(String value) {
        String text = text(value);
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }
}
