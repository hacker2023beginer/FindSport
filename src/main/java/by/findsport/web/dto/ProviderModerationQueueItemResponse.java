package by.findsport.web.dto;

import by.findsport.domain.ProviderType;
import java.time.Instant;

/**
 * Элемент очереди подтверждения профилей поставщиков.
 *
 * @param revision {@code true} — правки опубликованного профиля, {@code false} — запрос на подтверждение нового
 */
public record ProviderModerationQueueItemResponse(
        Long submissionId,
        Long providerId,
        ProviderType type,
        String name,
        Instant submittedAt,
        boolean revision) {
}
