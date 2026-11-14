package by.findsport.service;

import by.findsport.domain.Provider;
import by.findsport.domain.ProviderModeration;

/**
 * Профиль поставщика в кабинете владельца.
 *
 * @param provider действующая (для опубликованного профиля — публично видимая) версия
 * @param revision правки опубликованного профиля, еще не одобренные модератором; {@code null} — правок нет
 */
public record ProviderProfileView(Provider provider, ProviderModeration revision) {
}
