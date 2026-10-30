package by.findsport.domain;

/**
 * Статусы заявки: обработка поставщиком (ТЗ п. 4.2.10–4.2.11) и отмена пользователем (решение PM).
 * Место занимают только {@link #SUBMITTED} и {@link #CONFIRMED}.
 */
public enum BookingStatus {
    /** Подана и ожидает обработки. */
    SUBMITTED,
    CONFIRMED,
    REJECTED,
    /** Отменена пользователем — место освобождается (п. 4.9.7). */
    CANCELLED_BY_USER
}
