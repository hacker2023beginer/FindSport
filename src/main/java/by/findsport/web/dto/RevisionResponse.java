package by.findsport.web.dto;

import by.findsport.domain.CardStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;
import java.time.Instant;

/**
 * Неодобренные правки опубликованной карточки.
 *
 * @param status          {@code ON_MODERATION} — ожидают решения; {@code REJECTED} — отклонены
 * @param statusLabel     подпись для автора («На модерации» / «Требует доработки»)
 * @param proposedVersion предлагаемая версия; передается только в детальной карточке
 */
public record RevisionResponse(
        CardStatus status,
        String statusLabel,
        String rejectionReason,
        Instant submittedAt,
        @JsonRawValue @JsonInclude(JsonInclude.Include.NON_NULL) String proposedVersion) {
}
