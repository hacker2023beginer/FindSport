package by.findsport.web.dto;

import by.findsport.domain.CardStatus;
import by.findsport.domain.EventType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Карточка в кабинете поставщика: обе языковые версии действующей версии, статус с подписью для автора
 * («Требует доработки» для отклоненной) и причина отклонения (ТЗ п. 4.14.3).
 * Для опубликованной карточки с неодобренными правками {@code revision} содержит их состояние
 * и предлагаемую версию, пока публично показывается прежняя.
 */
public record ProviderEventResponse(
        Long id,
        Long providerId,
        CardStatus status,
        String statusLabel,
        String rejectionReason,
        RevisionResponse revision,
        EventType type,
        LocalizedTextDto title,
        LocalizedTextDto description,
        LocalizedTextDto conditions,
        LocalizedTextDto program,
        LocalizedTextDto skillLevel,
        List<Long> sportIds,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        Long regionId,
        AddressDto address,
        Integer ageMin,
        Integer ageMax,
        Integer participantLimit,
        OffsetDateTime registrationDeadline,
        String contactPhone,
        String contactEmail,
        List<Offer> offers,
        Instant createdAt,
        Instant updatedAt) {

    public record Offer(Long id, LocalizedTextDto title, BigDecimal price, String currency) {
    }
}
