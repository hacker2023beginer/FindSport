package by.findsport.domain;

/**
 * Статусы карточки (ТЗ п. 4.2.8, 4.14).
 */
public enum CardStatus {
    DRAFT,
    ON_MODERATION,
    PUBLISHED,
    /** Отклонена модератором с обязательной причиной; автор может доработать и отправить повторно. */
    REJECTED,
    ARCHIVED
}
