package by.findsport.web.dto;

import by.findsport.domain.ModerationDecision;
import java.time.Instant;

/**
 * Запись журнала модерации (ТЗ п. 4.13.4): отправка и решение по ней. Участники указываются
 * идентификаторами учетных записей.
 *
 * @param decision {@code null} — отправка ждет решения
 */
public record ModerationHistoryItemResponse(
        Long submissionId,
        Instant submittedAt,
        Long submittedById,
        ModerationDecision decision,
        String reason,
        Long moderatorId,
        Instant decidedAt) {
}
