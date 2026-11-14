package by.findsport.service;

import by.findsport.domain.Event;
import by.findsport.domain.EventMedia;
import java.util.List;

/**
 * Опубликованная карточка с вычисляемым статусом регистрации (ТЗ п. 4.9.7, 5.3).
 *
 * @param freeSeats свободные места; {@code null} — лимит не установлен
 * @param media     только одобренные модератором материалы (п. 4.14.1)
 */
public record PublicEventCard(Event event, Integer freeSeats, boolean registrationOpen, List<EventMedia> media) {
}
