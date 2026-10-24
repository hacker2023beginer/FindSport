package by.findsport.web.dto;

import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * Элемент очереди модератора.
 *
 * @param revision {@code true} — правки опубликованной карточки (публично видна прежняя версия),
 *                 {@code false} — новая карточка
 */
public record ModerationQueueItemResponse(
        Long submissionId,
        Long eventId,
        String title,
        String providerName,
        OffsetDateTime startsAt,
        Instant submittedAt,
        boolean revision) {
}
