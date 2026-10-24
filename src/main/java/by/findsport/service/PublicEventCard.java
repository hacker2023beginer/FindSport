package by.findsport.service;

import by.findsport.domain.Event;

/**
 * Опубликованная карточка с вычисляемым статусом регистрации (ТЗ п. 4.9.7, 5.3).
 *
 * @param freeSeats свободные места; {@code null} — лимит не установлен
 */
public record PublicEventCard(Event event, Integer freeSeats, boolean registrationOpen) {
}
