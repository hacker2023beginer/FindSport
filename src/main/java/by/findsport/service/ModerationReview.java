package by.findsport.service;

import by.findsport.domain.Event;
import by.findsport.domain.EventModeration;
import java.util.List;

/**
 * Материалы для принятия решения модератором (ТЗ п. 4.14.2–4.14.3).
 *
 * @param approvedSnapshot снимок действующей опубликованной версии; {@code null} — карточка еще не публиковалась
 * @param changedFields    поля, отличающиеся от опубликованной версии (Diff-режим)
 */
public record ModerationReview(Event event, EventModeration submission, String approvedSnapshot, List<String> changedFields) {
}
