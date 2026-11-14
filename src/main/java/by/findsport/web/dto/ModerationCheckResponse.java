package by.findsport.web.dto;

/** Результат автоматической проверки отправки (ТЗ п. 4.14.2). */
public record ModerationCheckResponse(String code, boolean passed, String message) {
}
