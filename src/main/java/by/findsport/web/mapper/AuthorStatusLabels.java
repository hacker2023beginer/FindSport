package by.findsport.web.mapper;

import by.findsport.domain.CardStatus;
import by.findsport.domain.Language;

/**
 * Подписи статусов карточки для автора. Отклоненная карточка хранится в БД как {@link CardStatus#REJECTED},
 * а автору показывается как «Требует доработки» (ТЗ п. 4.14.3, решение PM).
 */
public final class AuthorStatusLabels {

    private AuthorStatusLabels() {
    }

    public static String label(CardStatus status, Language language) {
        boolean en = language == Language.EN;
        return switch (status) {
            case DRAFT -> en ? "Draft" : "Черновик";
            case ON_MODERATION -> en ? "Under moderation" : "На модерации";
            case PUBLISHED -> en ? "Published" : "Опубликована";
            case REJECTED -> en ? "Needs revision" : "Требует доработки";
            case ARCHIVED -> en ? "Archived" : "Архивирована";
        };
    }
}
