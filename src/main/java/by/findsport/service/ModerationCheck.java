package by.findsport.service;

/**
 * Результат автоматической проверки отправки перед решением модератора (ТЗ п. 4.14.2).
 * Проверки носят справочный характер: решение принимает модератор.
 */
public record ModerationCheck(String code, boolean passed, String message) {
}
