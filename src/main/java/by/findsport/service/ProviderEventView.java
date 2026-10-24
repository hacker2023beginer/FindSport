package by.findsport.service;

import by.findsport.domain.Event;
import by.findsport.domain.EventModeration;

/**
 * Карточка в кабинете автора.
 *
 * @param event    действующая (для опубликованной карточки — публично видимая) версия
 * @param revision правки опубликованной карточки, еще не одобренные модератором: ожидают решения
 *                 или отклонены с причиной; {@code null} — правок нет
 */
public record ProviderEventView(Event event, EventModeration revision) {
}
