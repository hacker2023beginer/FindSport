package by.findsport.service;

import by.findsport.domain.Provider;
import by.findsport.domain.ProviderModeration;
import java.util.List;

/**
 * Материалы для подтверждения профиля поставщика (ТЗ п. 4.10.5, 4.14.3).
 *
 * @param approvedSnapshot снимок действующей опубликованной версии; {@code null} — профиль еще не публиковался
 * @param changedFields    поля, отличающиеся от опубликованной версии (Diff-режим)
 */
public record ProviderModerationReview(Provider provider, ProviderModeration submission, String approvedSnapshot,
                                       List<String> changedFields) {
}
