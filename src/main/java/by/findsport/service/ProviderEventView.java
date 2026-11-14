package by.findsport.service;

import by.findsport.domain.Event;
import by.findsport.domain.EventMedia;
import by.findsport.domain.EventModeration;
import java.util.List;

/**
 * Карточка в кабинете автора.
 *
 * @param event    действующая (для опубликованной карточки — публично видимая) версия
 * @param revision правки опубликованной карточки, еще не одобренные модератором: ожидают решения
 *                 или отклонены с причиной; {@code null} — правок нет
 * @param media    все материалы карточки с их отличием от публичной версии; пусто в списках
 */
public record ProviderEventView(Event event, EventModeration revision, List<EventMedia> media) {

    public ProviderEventView(Event event, EventModeration revision) {
        this(event, revision, List.of());
    }
}
